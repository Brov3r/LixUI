<div align="center">
    <img alt="Header" width="100%" src="./assets/readme_header.svg" />
</div>

<p align="center">
    <a href="https://github.com/Brov3r/LixUI/actions/workflows/gradle.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/Brov3r/LixUI/gradle.yml?branch=main&label=build"></a>
    <a href="https://github.com/Brov3r/LixUI/actions/workflows/codeql.yml"><img alt="CodeQL" src="https://img.shields.io/github/actions/workflow/status/Brov3r/LixUI/codeql.yml?branch=main&label=CodeQL"></a>
    <img alt="Java Version" src="https://img.shields.io/badge/Java-25-orange">
    <img alt="GitHub License" src="https://img.shields.io/github/license/Brov3r/LixUI?style=flat">
</p>

**LixUI** is a lightweight retained UI framework for LWJGL.

## ✨ Features

**WIP**

## 🚀 Quick Start

**WIP**

## 🧪 Running the Demos

Both demos render the same sample dashboard.

```shell
./gradlew demoOpenGl
./gradlew demoVulkan
./gradlew demoBoth
```

`demoBoth` opens two windows side by side, each occupying half of the primary monitor's work area
while keeping the 1280×720 (16:9) aspect ratio. The windows are raised above other applications on
start.

- **OpenGL:** requires OpenGL 3.3 Core; the demo requests a 4.5 Core context.
- **Vulkan:** requires a Vulkan-capable driver and loader. Swapchain images are recreated on resize
  and presented with FIFO synchronization.

## ⚖️ License

This project is licensed under the [MIT License](./LICENSE).
