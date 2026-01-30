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
3.  Run the main application class to start the simulation window.

---
*Note: This project is currently under active development for university coursework.*
