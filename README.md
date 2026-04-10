# JEE War Room 🎯

A minimalist Android app to track your JEE preparation progress across Physics, Chemistry, and Mathematics.

## 📱 About

JEE War Room helps students manage their JEE study progress with a clean, no-nonsense interface. Mark chapters as Weak, Review, or Mastered, attach PDF notes, and visualize your progress at a glance.

Built with the philosophy: **No shortcuts, just consistent progress.**

## ✨ Features

- **Subject Dashboard** - Visual progress rings showing weak/review/mastered chapters for Physics, Chemistry, and Maths
- **Chapter Management** - Add, delete, and organize chapters with long-press delete confirmation
- **PDF Note Attachment** - Link PDF notes to specific chapters for quick access
- **Status Filtering** - Filter chapters by Weak/Review/Mastered status
- **Persistent Storage** - All data saved locally using SharedPreferences

*screenshot*

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture:** Single-activity architecture with composable navigation
- **Data Persistence:** SharedPreferences with Gson serialization
- **Min SDK:** API 24 (Android 7.0)
- **Target SDK:** Latest

## 📸 Screenshots

### Main Dashboard
**[ADD SCREENSHOT HERE: Dashboard view with Physics, Chemistry, Maths circles]**

### Chapter List
**[ADD SCREENSHOT HERE: Chapter list showing different status chapters with action buttons]**

### Smart Action Buttons
**[ADD SCREENSHOT HERE: Close-up showing "Mark for Review" (red), "Mark Completed" (yellow), and "✨ Chill" (green)]**

### Terms & Conditions
**[ADD SCREENSHOT HERE: First-time onboarding screen with motivational message]**

## 🚀 Installation
### Using Android Studio ###
1. Clone the repository:
```bash
git clone https://github.com/yourusername/jee-war-room.git
```

2. Open the project in Android Studio

3. Build and run on your device or emulator


**No external dependencies or API keys required!**

### Installing apk ###

Go to *Releases* and download latest stable release version.apk file and install


## 📂 Project Structure

```
app/src/main/java/com/example/jeewarroom/
└── MainActivity.kt          # Single-file app (all code here)

app/src/main/
└── AndroidManifest.xml      # App manifest
```

**Single-file architecture** - Everything lives in `MainActivity.kt` for simplicity.

## 💡 Usage

### Adding Chapters
- Tap the `+` FAB button on any subject's chapter list
- Enter chapter name and save

### Updating Chapter Status
- **Mark for Review** - Click button on weak (red) chapters
- **Mark Completed** - Click button on review (yellow) chapters  
- **✨ Chill** - Green chapters are done (no action needed)
- **Revert Status** - Use the refresh icon to go backwards

### Attaching Notes
- Tap the `+` icon next to any chapter
- Select a PDF file from your device
- File is linked and can be opened anytime

### Deleting Chapters
- **Long-press** on any chapter name
- Confirm deletion in the dialog

### Filtering
- Tap "Filter" button on chapter list
- Toggle Weak/Review/Mastered checkboxes
- List updates instantly

## 🎨 Design Philosophy

- **Minimalist UI** - Clean, distraction-free interface
- **Performance First** - Optimized LazyColumn with proper keys and memoization
- **No Bloat** - Single-file app, no heavy libraries
- **Material 3** - Modern Android design standards
- **Accessible** - Clear visual indicators and touch targets

## 🔧 Key Implementation Details

### Performance Optimizations
- LazyColumn with stable item keys for efficient recomposition
- Memoized filter calculations to reduce unnecessary recompositions
- Minimal dependencies for fast build times

### Data Model
```kotlin
enum class Subject { PHYSICS, CHEMISTRY, MATHS }
enum class Status { RED, YELLOW, GREEN }
data class Chapter(id, name, subject, status, noteUri, order)
```

### State Management
- Compose `mutableStateListOf` for reactive UI updates
- SharedPreferences for persistent storage
- Gson for JSON serialization

## 🤝 Contributing

Contributions are welcome! Feel free to:
- Report bugs
- Suggest new features
- Submit pull requests

**Keep it simple** - this app's strength is its minimalism.

## 📝 License

This project is open source and available under the [MIT License](LICENSE).

## 🎓 Built By

A JEE aspirant who needed a simple way to track preparation progress.

**Good luck with your prep! 💪**

---

### Future Ideas (Maybe)
- Dark mode
- Export/import data
- Study session timer
- Statistics and analytics
- Cloud sync (optional)

**Note:** These are just ideas. The current version intentionally stays simple and focused.
