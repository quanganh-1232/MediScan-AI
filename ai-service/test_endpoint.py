import requests
from PIL import Image
import numpy as np

# Create a dummy image
img = np.zeros((100, 100, 3), dtype=np.uint8)
Image.fromarray(img).save("dummy.png")

with open("dummy.png", "rb") as f:
    res = requests.post("http://localhost:8000/predict", files={"file": ("dummy.png", f, "image/png")})

print(res.status_code)
print(res.text)
