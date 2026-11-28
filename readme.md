# Swift KeyM 🎮📱
**Play PC games on your phone using real mobile touch controls — zero OTG cables, zero root, zero paywalls.**

Made for cloud gamers (GeForce NOW, Moonlight, Steam Link, Parsec) who are tired of clunky virtual Xbox controllers or paywalled keymappers. Swift KeyM gives you a proper **custom mobile HUD editor** paired with a **full-time virtual mouse and keyboard** so you can play shooters like *Warzone* or sandbox games like *Minecraft Java* just like a native mobile game.

> **Created by St3rlight • Team StarDevs**  
> **Latest Release:** `v1.0.1-fixed`

---

## Why We Made This
Most Android keymappers work backwards — they take a *physical* keyboard and mouse and map them onto mobile games. We needed the exact opposite: **putting custom mobile touchscreen buttons, a smooth WASD joystick, and true mobile-style swipe-to-aim on top of PC cloud streams** without needing physical hardware or root access.

---

## Features That Actually Matter
* **Live Custom HUD Editor:** Spawn blank buttons anywhere, drag and resize them live over your game, customize opacity/corner radius, or slap your own **emojis & custom images** right onto the buttons.
* **Visual Key & Mouse Picker:** No annoying giant dropdown lists — just tap a slot and pick directly from a visual QWERTY keyboard or `MB1` / `MB2` / `Scroll` buttons.
* **Full-Time Virtual Mouse & Keyboard (`/dev/uinput`):** Powered by Shizuku to register real hardware input devices at the system level. Supports simultaneous multi-touch (`FLAG_SPLIT_TOUCH`) so you can run with `WASD`, hold `Scope`, hold `Shoot`, and swipe your camera at the exact same time without anything freezing.
* **3D Look Mode + Cursor Mode:** Swipe the right side of your screen for 360° camera turns, or flip to **Cursor Mode** to move a visible pointer when looting or navigating PC menus.
* **Zero Screen-Freeze Design:** Every button is its own floating window, plus there's a persistent **Floating Eye (`👁`)** button at the top to hide your whole HUD with one tap whenever you need to use your phone normally.
* **Floating QWERTY Typing Keyboard (`⌨`):** Pop up a draggable mini keyboard anytime to type in game chat or login screens without turning off the overlay.
* **Gyroscope & Recoil Helpers:** Full X/Y sensitivity, Invert X/Y, Always / ADS-Only / Fire-Only gyro modes, plus fine decimal recoil stabilization and custom response curves.
* **Per-App Game Library & Share Codes:** Link your favorite games to specific presets, auto-hide the HUD when you switch apps, and share layouts with friends using `SKM1://` share codes or `.skm` backup bundles.

---

## ⚠️ Important Setup & Requirements (Read This First!)
To keep everything working smoothly without root, make sure you follow these steps carefully:

1. **Shizuku is Required:**  
   Swift KeyM uses **Shizuku** to create the virtual `/dev/uinput` mouse and keyboard. Make sure Shizuku is installed, started (via Wireless Debugging or ADB), and authorized before turning on the overlay.
2. **Android 13 / 14 / 15+ "Restricted Settings":**  
   Because this APK is installed outside the Google Play Store, Android will lock the **"Display over other apps"** permission by default.  
   * **How to fix:** Open Swift KeyM -> tap **Unlock Restricted Settings** -> tap the **3 dots (`⋮`)** in the top-right corner of the App Info screen -> tap **Allow restricted settings** -> go back and grant Overlay permission.
3. **Properly Shutting Down the Service:**  
   Always use the **Turn Off Service** button inside Swift KeyM's floating Gear (`⚙`) menu (or the app's main toggle) when you are done playing so it cleanly unregisters the virtual keyboard and restores your normal phone keyboard.

---

## Quick Start Guide
1. Start **Shizuku** on your phone.
2. Open **Swift KeyM** and complete the permission checklist (**Overlay Permission** + **Authorize Shizuku**).
3. Tap **HUD & Bind Editor** to build your layout (or load one of the built-in presets).
4. Add your cloud gaming app (like **GeForce NOW**) to the **Game Library**, link your preset, and hit **Launch**.
5. In-game, tap the top-center **Gear (`⚙`)** icon anytime to tweak sensitivity, adjust gyro, edit buttons live, or switch presets.

---

## Fair Play Notice
Swift KeyM is strictly a **virtual input and custom HUD tool** for accessibility and mobile ergonomics. It does not read game memory, inject into game processes, or provide automated aim/screen-reading bots. Always follow the Terms of Service of the games and cloud platforms you play on.

---

**Built with ❤️ by St3rlight • Team StarDevs**
