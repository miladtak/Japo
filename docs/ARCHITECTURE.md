# Architecture

Japo is local-first. UI owns interaction; playback, project storage, logging, backup, and processing boundaries are separate modules.

Boundaries: ui, video, decoder, encoder, chroma, segmentation, matting, tracking, effects, layers, timeline, export, models, projects, backup, logging, settings.

All media processing is intended to stay on-device.
