# Shared Health Plugin for Minecraft

A Minecraft plugin that synchronizes health and hunger among all players, creating a truly shared survival experience. When a player dies, the plugin generates a brand new world for everyone to explore together.

---

## 🌟 Features

- **Shared Health System**: All players maintain the same health level.
- **Shared Hunger System**: Food levels are synchronized across all players.
- **World Generation**: Automatically creates new worlds when a player dies.
- **Waiting Area**: Players are moved to a safe zone during world generation.
- **Visual Feedback**: Displays a progress bar and messages during world creation.
- **World Cleanup**: Automatically removes old worlds, keeping only the 2 most recent ones.

---

## 🛠 Installation

1. Build the plugin using Maven:

    ```bash
    mvn clean package
    ```

2. Copy the generated `.jar` file from the `target/` directory into your server’s `plugins/` folder.

3. Restart or reload your Minecraft server.

---

## ⚙ Configuration

The plugin currently uses default settings. Configurable options may be added in future releases.

---

## 💬 Commands

No commands are implemented at this time — all features function automatically.

---

## 🔍 How It Works

- **Health & Hunger Sync**:  
  When a player takes damage or heals, all players' health and hunger levels are updated to match.

- **On Death**:  
  1. All players are teleported to a waiting area.  
  2. A new world is generated with progress feedback.  
  3. All players are teleported to the new world with inventories reset.  
  4. Old worlds are cleaned up automatically (only the 2 most recent are kept).

---

## 🧱 Requirements

- Minecraft Server (PaperMC recommended) version **1.21**
- **Java 21**

---

## ⚠ Known Issues

- World generation can be slow on servers with limited performance.
- Edge cases may exist involving player disconnection during transitions.

---

## 🤝 Contributing

Contributions are welcome!  
Feel free to open an [issue](https://github.com/MarcoCoelho18/SharedHealth/issues) or submit a [pull request](https://github.com/MarcoCoelho18/SharedHealth/pulls) on GitHub.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

Enjoy a new kind of shared survival in Minecraft! 🌍🛡🍗