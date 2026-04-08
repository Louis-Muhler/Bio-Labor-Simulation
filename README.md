# Bio-Labor Simulation

Semester project (Software Engineering 2): a real-time simulation of an evolving microbial ecosystem.

The main focus is a robust implementation of **multithreading and concurrency** together with an interactive Swing UI.

## Project Overview

- Simulation of many autonomous microbes with inheritance, mutation, selection, and environmental pressure
- Real-time visualization with camera, selection/inspector, overlays, and debug mode
- Persistent saves and settings including autosave
- Architecture with clear separation between UI, session lifecycle, and simulation core

## Architecture (Current)

### High-Level Flow

1. `BioLabSimulatorApp` controls UI flow and top-level states.
2. `SimulationSessionCoordinator` starts/stops runtime sessions (engine, canvas, overlays, loop).
3. `SimulationLoopController` schedules updates (TPS) and rendering (FPS) independently.
4. `SimulationEngine` executes frames via `SimulationUpdateService`.
5. `SimulationFrameOrchestrator` partitions microbes into chunks and processes them in parallel.
6. `PopulationCommitSystem` commits results atomically and publishes a new `SimulationSnapshot`.

### Key Components

- `SimulationEngineContext`: wiring of engine services
- `FrameMutationCoordinator`: exclusivity between frame updates and exclusive mutations (for example load/capture/spawn)
- `SimulationCommandProcessor`: thread-safe command queue with coalescing for high-frequency slider inputs
- `SpatialGrid` and `MicrobeGrid`: spatial indices for local neighborhood queries

## Concurrency Model

### Core Principles

- **Single writer per frame commit**: world lists are mutated only during commit under `worldState.dataLock()`.
- **Parallel worker phase**: microbe behavior runs in chunks on an `ExecutorService`.
- **Atomic snapshot publication**: UI reads a `volatile` `SimulationSnapshot` lock-free.
- **Exclusive mutations**: save/load/debug spawn do not interleave with an active frame.

### Tick vs Render

- Tick speed via `SimulationLoopController.cycleSpeed()`:
  - `1x`, `2x`, `5x`, `10x`, `25x`, `50x`, `100x`, `MAX`
- Render FPS is configured separately via `setRenderFps(int)` (internally clamped to `10..240`)

## Requirements

- Java 17+
- Maven 3.9+
- Windows, Linux, or macOS (primarily tested on Windows)

## Build, Test, Run

Run from the project root:

```powershell
mvn clean compile
mvn test
mvn exec:java -Dexec.mainClass="com.biolab.BioLabSimulatorApp"
```

Optional package build:

```powershell
mvn clean package
```

## Features

- Real-time simulation with herbivore/carnivore behavior paths
- Adjustable environment parameters (temperature, toxicity, food spawn rate)
- Overlay system (Inspector, Environment, World Stats, Creator)
- Main menu, save browser, and settings flows via `AppUiStateMachine`
- Autosave via `SessionSaveCoordinator` + `AsyncSaveService`
- Debug mode (AI intent lines, radius, IDs, simulation-rate display)

## Storage Locations

- Settings: `%LOCALAPPDATA%/BioLabSimulator/settings`
- Saves: `%LOCALAPPDATA%/BioLabSimulator/saves`

## Known Limitations

- Large spawn bursts can still be expensive because spawn operations currently run individually through the runtime.
- At very high population density, lock contention (`Microbe.stateLock`) can limit parallel speedup.

## Quality Assurance

- Extensive unit tests in `src/test/java/com/biolab`
- Concurrency-focused coverage (for example `FrameMutationCoordinatorTest`, `SimulationUpdateServiceTest`,
  `SimulationEngineAtomicStateTest`)

---
Project status: Completed (final/semester project).
