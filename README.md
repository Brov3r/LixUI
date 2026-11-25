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

- Retained, backend-neutral widget tree with `HBox`, `VBox`, `Grid`, `Flow`, `Dock`, `Stack` and `Anchor` layouts.
- 70+ controls: inputs, selects, tables, trees, virtual lists, tabs, charts, dialogs, menus and tooltips.
- Shared draw list rendered by OpenGL 3.3 or Vulkan; gradients, rounded shapes, masks, textures and TrueType text.
- Theming, animations, drag and drop, keyboard and mouse input.
- Standalone toasts with custom content or a fully custom renderer.
- Windowing-library agnostic: feed `InputState` and a `RenderBackend`; logging through SLF4J.

## 🚀 Quick Start

Requires Java 25 and LWJGL 3.4. The fat JAR is built with `./gradlew shadowJar`.

```java
InputState input = new InputState();
WidgetManager ui = new WidgetManager(input, backend); // OpenGlBackend, VulkanLayer, ...
ui.resize(new Viewport(fbWidth, fbHeight, windowWidth, windowHeight));
ui.setDefaultFont(ui.context().createFont(FontData.from(path), 16));

Button hello = new Button("Hello");
hello.setOnAction(() -> System.out.println("hi"));
ui.add(hello);

// Each frame:
ui.update(deltaSeconds);
ui.render();
```

`GlfwInput.install(handle, input)` and `GlfwPlatform.install(handle)` connect a GLFW window. Custom rendering
goes through `ui.renderer()` between `begin()` and `end()`.

### Embedding into an existing app

```java
InputState input = new InputState();
try (GlfwInput adapter = GlfwInput.install(window, input);
     GlfwPlatform platform = GlfwPlatform.install(window);
     WidgetManager ui = new WidgetManager(input, backend)) {
    while (!GLFW.glfwWindowShouldClose(window)) {
        // ... the host's own update and scene rendering ...
        ui.resize(new Viewport(fbWidth, fbHeight, winWidth, winHeight)); // when it changes
        ui.update(deltaSeconds);
        ui.render();                    // draw UI over the host scene
        GLFW.glfwSwapBuffers(window);
    }
}
```

Closing the manager releases only its own fonts and textures; the backend and window stay with the host.
Skip the host's own picking where `ui.blocksPointer(x, y)` is true. With Vulkan, record the UI between
`VulkanLayer.beginFrame(cmd)`/`endFrame()` and call `composite(cmd)` inside the host render pass.

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

- **OpenGL:** requires an OpenGL 3.3 Core context.
- **Vulkan:** requires a Vulkan-capable driver and loader. Swapchain images are recreated on resize
  and presented with FIFO synchronization.

## ⚖️ License

This project is licensed under the [MIT License](./LICENSE).
