# NearPazham (நார் பழம்)

A strategic two-player board game for Android featuring both local and online multiplayer modes.

## Overview

NearPazham is a turn-based strategy game where two players compete to capture territory and outmaneuver their opponent. The game combines simple rules with deep strategic gameplay, offering both casual local play and competitive online matchmaking.

## Game Modes

### Local Mode
- **Pass-and-play multiplayer** on a single device
- Perfect for playing with friends in person
- No internet connection required
- Instant game start with no waiting

### Online Mode
- **Matchmaking system** to find opponents worldwide
- Real-time gameplay synchronization
- Queue persistence across app restarts
- Automatic game cleanup and state management

## Features

### Core Gameplay
- Strategic board-based movement and placement
- Turn-based mechanics with clear visual feedback
- Win condition detection with game-over handling
- Move validation and illegal move prevention

### User Experience
- **Dual-mode architecture** with seamless switching
- Enhanced queue UI with position tracking and estimated wait times
- Queue session restoration after app closure
- Persistent device and player identification
- Visual status indicators for game state

### Technical Features
- **Network resilience** with automatic retry mechanisms
- Thread-safe game state management
- Lifecycle-aware activity handling
- Comprehensive error handling and recovery
- Self-match prevention with multiple validation layers

## Tech Stack

### Android Framework
- **Language**: Java
- **Min SDK**: Android 5.0 (API 21)
- **Target SDK**: Android 13+ (API 33+)
- **UI Framework**: Android Views (XML layouts)

### Architecture Components
- **Lifecycle Management**: AndroidX Lifecycle components
- **Threading**: HandlerThread, Looper, Handler
- **Persistence**: SharedPreferences for state management
- **Material Design**: Material Components for UI elements

### Core Components

#### Game Logic
- `GameView`: Custom view for game board rendering and touch handling
- `GameState`: Game state management and move validation
- `Board`: Board representation and piece management
- `LocalGameManager`: Local multiplayer game orchestration
- `GamePollingService`: Server state synchronization for online games

#### Networking
- `NetworkService`: RESTful API client for server communication
- `QueueManager`: Matchmaking queue management with state machine
- `NetworkRetryManager`: Exponential backoff retry logic
- `QueuePersistenceManager`: Queue state persistence and restoration

#### Identity Management
- `PlayerIdGenerator`: Unique player ID generation with device fingerprinting
- `DeviceIdGenerator`: Persistent device identification
- Timestamp-based ID validation with expiration handling

### Design Patterns
- **State Machine**: Queue state management (IDLE, JOINING, IN_QUEUE, MATCH_FOUND, etc.)
- **Callback Pattern**: Asynchronous network operations and UI updates
- **Singleton Pattern**: Service managers and ID generators
- **Observer Pattern**: UI updates via listener interfaces

### Network Architecture
- **Protocol**: HTTP/HTTPS RESTful API
- **Data Format**: JSON for request/response payloads
- **Polling Strategy**: Periodic game state updates during active games
- **Error Handling**: Comprehensive failure recovery with user feedback

### UI/UX Implementation
- **Material Design Guidelines**: Consistent visual language
- **Responsive Layouts**: Weight-based layouts for different screen sizes
- **Visual Feedback**: Color-coded states, progress indicators, toast messages
- **Accessibility**: Proper content descriptions and touch target sizes

## Key Technical Highlights

### Robust ID Management
- Device fingerprinting using hardware identifiers
- Timestamp-based ID generation with validation
- Self-match prevention across multiple validation layers
- ID persistence with automatic regeneration when expired

### Queue Persistence
- Automatic queue state saving during matchmaking
- Session restoration after app restart or background
- Queue statistics tracking (time, attempts, outcomes)
- Position and estimated wait time updates

### Lifecycle Management
- Proper activity lifecycle handling (onCreate, onPause, onResume, onDestroy)
- Resource cleanup to prevent memory leaks
- Background/foreground state transitions
- Dialog state management across configuration changes

### Error Handling
- Network failure recovery with exponential backoff
- Invalid state detection and automatic correction
- User-friendly error messages with recovery options
- Graceful degradation when services are unavailable

## Project Structure

```
com.gfg.NearPazham/
├── MainActivity.java           # Main activity and UI orchestration
├── GameView.java              # Custom view for game rendering
├── GameState.java             # Game logic and state management
├── Board.java                 # Board representation
├── LocalGameManager.java      # Local game orchestration
├── NetworkService.java        # API client
├── QueueManager.java          # Matchmaking queue manager
├── NetworkRetryManager.java   # Retry logic
├── QueuePersistenceManager.java # State persistence
├── GamePollingService.java    # Server sync service
└── PlayerIdGenerator.java     # ID generation utilities
```

## Requirements

- Android device running Android 5.0 (Lollipop) or higher
- Internet connection for online mode
- ~50MB storage space

## Installation

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on device or emulator

## Future Enhancements

- ELO rating system for competitive play
- Game replay and move history
- Custom board themes and piece designs
- Tournament mode with bracket system
- Friend list and direct challenge system
- Chat functionality during games
- Statistics dashboard and achievement system

## License

[Specify your license here]

## Credits

Developed using Android Studio and the Android SDK.

---

**Note**: This game requires a backend server for online matchmaking. Server implementation details are not included in this client-side codebase.
