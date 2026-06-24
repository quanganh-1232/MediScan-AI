# Cấu trúc Dataset Huấn Luyện ANFIS

Để huấn luyện mô hình ANFIS mới bằng PyTorch (`anfis.py`), bạn cần chuẩn bị một bộ dữ liệu (dataset) dạng bảng (ví dụ file `.csv` hoặc `.xlsx`). Dữ liệu này gồm 2 phần chính: **Đầu vào (Features - X)** được tính toán từ các tọa độ YOLO/thuật toán xử lý ảnh, và **Đầu ra (Label - Y)** là điểm số rủi ro gãy xương thực tế do bác sĩ đánh giá.

Dưới đây là bảng các trường (cột) dữ liệu bạn cần tạo:

## Bảng các trường dữ liệu (Cột trong CSV/Excel)

| Tên trường (Cột) | Kiểu dữ liệu | Khoảng giá trị | Phân loại | Ý nghĩa & Nguồn gốc |
| :--- | :---: | :---: | :---: | :--- |
| **`image_id`** | Text | Bất kỳ | *Định danh* | (Tùy chọn) Tên file ảnh hoặc ID của bệnh nhân để bạn dễ quản lý, theo dõi. |
| **`edge_irregularity`** | Float | 0.0 - 1.0 | **Đầu vào (X)** | Mức độ không đều của bờ vỏ xương. Tính từ độ Fractal kết hợp độ tự tin YOLO. (0: rất nhẵn, 1: rất gồ ghề/mất liên tục). |
| **`bone_contrast`** | Float | 0.0 - 1.0 | **Đầu vào (X)** | Độ tương phản vỏ xương trên ảnh X-quang. (0: rất mờ, 1: rất rõ nét). |
| **`yolo_confidence`** | Float | 0.0 - 1.0 | **Đầu vào (X)** | Độ tự tin phát hiện vết gãy từ mô hình YOLO. (0: không phát hiện, 1: chắc chắn 100%). |
| **`region_burden`** | Float | 0.0 - 1.0 | **Đầu vào (X)** | Diện tích vùng tổn thương so với toàn bộ ảnh (dựa trên tọa độ bbox `x,y,w,h` của YOLO). (0: rất nhỏ, 1: rất lớn/nhiều vùng). |
| **`morphology_strength`** | Float | 0.0 - 1.0 | **Đầu vào (X)** | Hình thái của tổn thương (ví dụ tỷ lệ chiều dài/chiều rộng của bounding box từ YOLO). Giúp phân biệt đường gãy xiên/dài. (0: không rõ ràng, 1: rất giống vết gãy). |
| **`region_count`** | Integer | 0, 1, 2... | **Đầu vào phụ** | (Tùy chọn) Số lượng vùng (bounding boxes) mà YOLO phát hiện được trên ảnh. |
| **`fracture_score`** | Float | 0 - 100 | **ĐẦU RA (Y)** | **Nhãn thực tế (Ground Truth):** Điểm đánh giá nguy cơ gãy xương do *Bác sĩ con người (chuyên gia)* chấm điểm cho bức ảnh này. (0: Chắc chắn không gãy, 100: Gãy rất nghiêm trọng). |

> [!TIP]
> **Ghi chú về Đầu ra (Y):** Mô hình PyTorch ANFIS hiện tại là dạng dự đoán biến liên tục. Do đó, bạn cần có một cột `fracture_score` (Điểm từ 0 đến 100) làm "Nhãn" để hệ thống tính toán sai số và học hỏi.

## Hướng dẫn các bước tạo Dataset

1. **Thu thập ảnh và kết quả:** Chạy code hệ thống hiện tại của bạn qua hàng loạt ảnh X-quang.
2. **Trích xuất thông số:** Đối với mỗi ảnh, gọi hàm `build_diagnosis_features` trong `features.py` (hàm này tính toán dựa trên tọa độ bbox từ YOLO). Nó sẽ trả về một Dictionary chứa 5 giá trị đầu vào (X) như bảng trên.
3. **Xin ý kiến Bác sĩ:** Đưa ảnh X-quang đó cho bác sĩ hoặc chuyên gia đọc phim. Yêu cầu bác sĩ đánh giá điểm số gãy xương (ví dụ: gãy rõ ràng cho 95 điểm, bình thường cho 10 điểm). Điền điểm này vào cột `fracture_score` (Y).
4. **Lưu file:** Lưu tất cả thành 1 file CSV. Mỗi hàng là 1 bức ảnh.

Khi bạn có khoảng 200 - 500 dòng (ảnh) như vậy, bạn có thể truyền file CSV này vào mô hình PyTorch trong file `experimental.py` để chạy hàm `train_anfis()`.
