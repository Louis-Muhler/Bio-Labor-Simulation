# Bio-Labor Simulation

This repository contains a semester project for the **Software Engineering 2** course.

The project is a real-time simulation of a biological ecosystem. The primary goal is to implement a multithreaded application that manages a large number of autonomous agents (microbes/entities) interacting with their environment.

## Project Concept

The simulation creates an environment where entities evolve over time. The core loop revolves around natural selection: agents with traits better suited to the current environment survive and reproduce, while others perish.

The project is designed to be modular and scalable. We are starting with a basic implementation of movement and survival logic, but the architecture allows us to layer in more complex behaviors, genetic variations, and environmental stressors as development progresses.

**Key Objectives:**
* Simulate a large population of independent entities.
* Implement evolutionary mechanisms (selection, mutation, inheritance).
* Allow user interaction to influence the simulation parameters dynamically.

## Technical Focus

The main technical requirement for this project is **Multithreading**.

Simulating thousands of agents calculating their behavior, movement, and interactions simultaneously requires efficient resource management. We are using Java's concurrency tools to parallelize the workload, ensuring the simulation remains fluid and responsive even under high load.

**Tech Stack:**
* Java
* Java Swing (for the visualization)
* Java Concurrency Utilities

## Getting Started

1.  Clone the repository.
2.  Open the project in your preferred Java IDE (IntelliJ, Eclipse, VS Code).
3.  Build the project using Maven: `mvn clean compile`
4.  Run the main application class to start the simulation window: `mvn exec:java -Dexec.mainClass="com.biolab.BioLabSimulatorApp"`

## Features

### Interactive Simulation
- Real-time visualization of microbe population with genetic traits
- Adjustable environmental parameters (temperature and toxicity)
- Natural selection mechanics where microbes evolve based on environmental pressures

### Settings System
- **Persistent Configuration**: Settings are automatically saved and loaded from `~/.biolabsim/settings.properties`
- **Settings Overlay**: Access via File menu → Settings or keyboard shortcut (Ctrl+S / Cmd+S)
  - Automatically pauses simulation when open
  - Semi-transparent overlay with blur effect
  - Close with ESC key or Cancel button

### Display Options
- **Fullscreen Mode**: Toggle between windowed and fullscreen display
- **Multiple Resolutions**: Support for various screen resolutions:
  - 16:9 ratios: 1280x720 (HD), 1920x1080 (Full HD), 2560x1440 (QHD), 3840x2160 (4K UHD)
  - 21:9 ultrawide: 2560x1080, 3440x1440 (WQHD)
  - 16:10: 1440x900, 1680x1050, 1920x1200
  - 4:3: 1024x768, 1280x960, 1600x1200

### Performance Options
- **Configurable FPS**: Choose target frame rate (30, 60, 120, 144, or Unlimited)
- **Robust Error Handling**: Configuration file errors are handled gracefully with fallback to defaults

---
*Note: This project is currently under active development for university coursework.*
