# Japo implementation status

## Implemented and wired
- Android project foundation
- Local video import through Storage Access Framework
- Persistent URI permission
- Camera-app video capture and automatic import
- Media3 video playback with seek controls
- Local project persistence
- Timeline clip and layer metadata persistence
- Local error log
- Text backup
- Bundled ML Kit selfie/person segmentation on the current extracted frame
- Interactive mask brush editor component
- IoU-based multi-person tracking engine with stable IDs
- Temporal alpha-mask smoothing engine
- Local alpha-mask refinement component
- Local RGB chroma-key processing on the current extracted frame
- Real MP4 export through Media3 Transformer
- Source-aware timeline clip model and multi-clip concatenation export backend
- Trim start/end controls
- Grayscale, invert, brightness and contrast export effects
- Local export progress/error handling
- GitHub Actions debug APK build workflow

## Still under construction
- Full-video segmentation/matting pipeline across every frame
- Hair/finger-level dedicated matting model
- True multi-person identity tracking and Person 1/2/3 workflows
- Temporal consistency across exported frames
- Chroma key controls UI and per-frame/full-video compositor integration
- Background image/video replacement compositor
- Transparent/alpha video export formats
- Full manual mask editor workflow with undo/redo history and edge-refine tools
- Dedicated timeline UI with clip splitting, ordering and layer controls
- True Anime/Cartoon/Pencil/Ink/Watercolor/Oil neural stylization
- Final optimization and device matrix testing

A feature is not marked complete merely because an interface exists; it is marked complete only after its real implementation is wired into the application.
