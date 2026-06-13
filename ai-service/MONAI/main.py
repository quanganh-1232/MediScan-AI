# main.py
from fastapi import FastAPI, UploadFile, File
import torch
import monai
# Chỉ import những "công thức/model" bạn cần từ MONAI
from monai.networks.nets import FasterRCNN 

app = FastAPI()

# 1. Khởi tạo và Load model đã được train (file .pth của bạn)
model = FasterRCNN(spatial_dims=2, in_channels=1, num_classes=2)
model.load_state_dict(torch.load("duong_dan_toi_file_trong_luong_model.pth"))
model.eval()

@app.post("/predict-fracture")
async def predict_fracture(file: UploadFile = File(...)):
    # 2. Đọc ảnh từ request do Java gửi sang
    image_bytes = await file.read()
    
    # 3. Chạy qua mô hình MONAI để lấy tọa độ gãy xương
    # (Viết logic tiền xử lý ảnh và dự đoán ở đây)
    
    # 4. Trả về kết quả (Ví dụ trả về tọa độ vùng khoanh [x1, y1, x2, y2])
    return {
        "status": "success",
        "bounding_boxes": [
            {"x_min": 100, "y_min": 150, "x_max": 200, "y_max": 250, "confidence": 0.95}
        ]
    }
