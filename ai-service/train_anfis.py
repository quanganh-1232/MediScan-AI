"""
Trains the fracture-risk ANFIS network by distillation from the existing
hand-written fuzzy rule system (anfis/model.py).

Why distillation instead of training on real labels: the project has no
labelled dataset yet (no confirmed ground-truth fracture scores tied to
images). The hand-written skfuzzy rules already encode the team's expert
knowledge about how edge irregularity, bone contrast, YOLO confidence,
region burden and morphology strength should combine into a fracture
risk score. Distilling that behaviour into an ANFIS turns a fixed,
non-differentiable rule table into a smooth, trainable network that:
  - reproduces today's clinical judgement (validated against the teacher
    below before being saved), and
  - can be fine-tuned later with `torch.optim` once real doctor-confirmed
    labels accumulate, without changing the surrounding service code.

Usage:
    cd ai-service
    python train_anfis.py [--samples 8000] [--epochs 60]

Output:
    anfis/weights/fracture_anfis.pt
"""

import argparse
import copy
import os
import time

import numpy as np
import torch
from torch.utils.data import DataLoader, TensorDataset

from anfis.experimental import calc_error
from anfis.fracture_model import INPUT_ORDER, build_fracture_anfis
from anfis.model import build_diagnosis_control_system

WEIGHTS_PATH = os.path.join(os.path.dirname(__file__), "anfis", "weights", "fracture_anfis.pt")


def generate_teacher_dataset(n_samples: int, seed: int = 42):
    """Sample the 5D feature space and label each point with the score the
    existing expert fuzzy system would produce for it."""
    rng = np.random.default_rng(seed)
    x = rng.uniform(0.0, 1.0, size=(n_samples, len(INPUT_ORDER))).astype(np.float32)

    simulation = build_diagnosis_control_system()
    y = np.empty((n_samples, 1), dtype=np.float32)
    for i in range(n_samples):
        for j, name in enumerate(INPUT_ORDER):
            simulation.input[name] = float(x[i, j])
        simulation.compute()
        # Match the production fallback in anfis/service.py: the hand-written
        # rule base is sparse and leaves many input combinations with no
        # rule firing at all, in which case skfuzzy has no defuzzified
        # output for "fracture_score". Production defaults that to 0.0
        # (very-low risk), so the distillation teacher must reproduce the
        # same behaviour rather than crash on it.
        y[i, 0] = float(simulation.output.get("fracture_score", 0.0))

    return x, y


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--samples", type=int, default=8000, help="number of synthetic training points")
    parser.add_argument("--val-samples", type=int, default=1000, help="number of held-out validation points")
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--lr", type=float, default=0.003)
    args = parser.parse_args()

    print(f"Generating {args.samples} training + {args.val_samples} validation points from the expert rule system...")
    t0 = time.time()
    x_train, y_train = generate_teacher_dataset(args.samples, seed=42)
    x_val, y_val = generate_teacher_dataset(args.val_samples, seed=1234)
    print(f"  done in {time.time() - t0:.1f}s")

    x_train_t = torch.tensor(x_train, dtype=torch.float)
    y_train_t = torch.tensor(y_train, dtype=torch.float)
    x_val_t = torch.tensor(x_val, dtype=torch.float)
    y_val_t = torch.tensor(y_val, dtype=torch.float)

    model = build_fracture_anfis(hybrid=True)
    loader = DataLoader(TensorDataset(x_train_t, y_train_t), batch_size=args.batch_size, shuffle=True)

    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)
    scheduler = torch.optim.lr_scheduler.StepLR(optimizer, step_size=20, gamma=0.5)
    # Mean (not sum) reduction: fracture_score lives on a 0-100 scale, and a
    # sum-reduced loss over a batch produces gradients large enough to blow
    # up the premise (membership-function) parameters within a few steps.
    criterion = torch.nn.MSELoss(reduction="mean")

    print(f"Training ANFIS for {args.epochs} epochs ({model.num_rules} rules)...")
    t0 = time.time()

    # Hybrid ANFIS learning alternates: backprop nudges the premise
    # (membership-function) parameters, then LSE re-solves the consequent
    # coefficients optimally for those premises. That combination isn't
    # monotonic — some epochs land on a worse fit than earlier ones — so we
    # track the best checkpoint by held-out validation RMSE instead of just
    # keeping whatever the last epoch happens to produce.
    best_val_rmse = float("inf")
    best_state = None

    for epoch in range(args.epochs):
        model.train()
        for xb, yb in loader:
            y_pred = model(xb)
            loss = criterion(y_pred, yb)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

        with torch.no_grad():
            model.fit_coeff(x_train_t, y_train_t)

        model.eval()
        with torch.no_grad():
            y_pred_val = model(x_val_t)
            _, val_rmse, _ = calc_error(y_pred_val, y_val_t)

        if val_rmse < best_val_rmse:
            best_val_rmse = val_rmse
            best_state = copy.deepcopy(model.state_dict())

        if epoch % 5 == 0 or epoch == args.epochs - 1:
            print(f"  epoch {epoch:3d}: val RMSE={val_rmse:.3f}  (best so far {best_val_rmse:.3f})")

        scheduler.step()

    print(f"  done in {time.time() - t0:.1f}s, best validation RMSE={best_val_rmse:.3f}")
    model.load_state_dict(best_state)

    model.eval()
    with torch.no_grad():
        y_pred_val = model(x_val_t)
        mse, rmse, _ = calc_error(y_pred_val, y_val_t)
        mae = torch.mean(torch.abs(y_pred_val - y_val_t)).item()
        max_err = torch.max(torch.abs(y_pred_val - y_val_t)).item()

    print("\n=== Best checkpoint vs. the expert rule system (held-out points) ===")
    print(f"  RMSE: {rmse:.3f} (score scale 0-100)")
    print(f"  MAE:  {mae:.3f}")
    print(f"  Max abs error: {max_err:.3f}")

    os.makedirs(os.path.dirname(WEIGHTS_PATH), exist_ok=True)
    torch.save(best_state, WEIGHTS_PATH)
    print(f"\nSaved trained weights to {WEIGHTS_PATH}")


if __name__ == "__main__":
    main()
