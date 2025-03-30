# PaceMan Tracker

A standalone application or Julti plugin to track and upload runs to PaceMan.gg.

## Usage

PaceMan Tracker is included as a default plugin in [Julti](https://github.com/DuncanRuns/Julti/releases) and [Jingle](https://github.com/DuncanRuns/Jingle/releases).
For those who don't/can't use Julti/Jingle, it can be [downloaded as an application and ran separately](https://github.com/PaceMan-MCSR/PaceMan-Tracker/releases/latest).

## Developing and Building

Both the plugin and standalone jars can be built using `./gradlew build`.

If you intend on changing GUI portions of the code, IntelliJ IDEA must be configured in a certain way to ensure the GUI form works properly:
- `Settings` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle` -> `Build and run using: IntelliJ Idea`
- `Settings` -> `Editor` -> `GUI Designer` -> `Generate GUI into: Java source code`
