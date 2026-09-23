# Japo implementation status

## Implemented and wired
- Android project foundation
- Local video import through Storage Access Framework
- Persistent URI permission
- Camera-app video capture and automatic import
- Media3 video playback with seek controls
- Local project persistence
- Timeline clip and layer metadata persistence
- Local error log and text backup
- ML Kit person/selfie segmentation for extracted frames
- Interactive mask brush editor component with undo/redo/reset/feather controls
- IoU-based multi-person tracking engine with stable IDs
- Temporal alpha-mask smoothing component with regression test
- Local alpha-mask refinement component
- Configurable reusable frame-processing pipeline combining segmentation, temporal smoothing, matting and chroma-key processing
- RGB chroma-key processing with similarity, threshold, smoothness, edge softness and spill suppression
- Real MP4 export through Media3 Transformer with trim
- Deterministic visual filters for grayscale/invert/brightness/contrast and stylized presets
- Timeline clip model and multi-clip concatenation export backend
- Local export progress/error handling
- Source validation workflow: Kotlin compilation, unit tests and Android lint
- No APK is produced by CI

## Still under construction
- Connect the frame-processing pipeline to a streaming full-video decoder/encoder
- Full-video segmentation/matting across every decoded frame
- Dedicated hair/finger-level neural matting model bundled locally
- True multi-person detection-to-mask tracking and Person 1/2/3 workflows
- Temporal consistency across the complete exported video
- Full chroma-key compositor and controls in the editing UI
- Background image/video/blur replacement through the complete export pipeline
- Transparent/alpha video export where the selected codec/container supports it
- Full manual mask editing workflow integrated with project/timeline state
- Full timeline UI with visible tracks, drag/reorder/split/delete and trim interaction
- Full layer UI integrated with project state
- True local neural Anime/Cartoon/Pencil/Ink/Watercolor/Oil/Illustration models
- Streaming/chunked memory management for long/high-resolution videos
- Device testing on Poco X3 Pro / Android 11 and final regression pass

A feature is not marked complete merely because an interface exists; it is marked complete only after its real implementation is wired into the application and validated.
