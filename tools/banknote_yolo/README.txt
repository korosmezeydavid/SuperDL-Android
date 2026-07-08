Super DL – HUF YOLO dataset
Generated: 2026-06-26 20:32 UTC
Source: tools\banknote_dataset_synth

Train:
  cd tools
  yolo detect train data=banknote_yolo/data.yaml model=yolo11n.pt imgsz=640 epochs=150 patience=25

Validate:
  yolo detect val data=banknote_yolo/data.yaml model=runs/detect/train/weights/best.pt

Export TFLite (Android):
  yolo export model=runs/detect/train/weights/best.pt format=tflite imgsz=640 int8

Manual label correction:
  1. Fix boxes in LabelImg / CVAT / Roboflow
  2. Re-run with --bbox-mode existing --labels-dir <your_labels>
