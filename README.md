# 📚 Comic Reader (Java Version)

An Android comic reader app built with **Java**, supporting **CBR**, **CBZ**, and **PDF** formats.  
This is the **original version** of the Comic Reader project — later rewritten in Kotlin for modernization.

---

## 🚀 Features
- 📖 Read comics in **CBR**, **CBZ**, and **PDF** formats  
- 🗂️ Browse comics from folders  
- 🖼️ Thumbnail page previews  
- ⏱️ Continue reading from last page  
- 🌓 Dark mode support  
- 🔄 Sort and filter comics  
- 🗑️ Clear recent history  

---

## 🧠 Built With
| Component | Technology |
|------------|-------------|
| Language | **Java** |
| Architecture | **MVVM (ViewModel + LiveData)** |
| UI | XML layouts |
| Database | Room (SQLite) |
| Image Loading | BitmapFactory |
| Min SDK | 26 |
| Target SDK | 35 |

---

## 📸 Screenshots

<img width="425" height="756" alt="comic" src="https://github.com/user-attachments/assets/639a16fe-fc0e-4c59-bfea-16ef5a0765f6" />  <img width="425" height="756" alt="recent" src="https://github.com/user-attachments/assets/04abdf83-3d2b-419e-a3c2-f381cf1c533b" />
<img width="425" height="756" alt="library" src="https://github.com/user-attachments/assets/6aaa6eed-e2e2-4b24-82ed-15ac10b28f97" />  <img width="425" height="756" alt="setting" src="https://github.com/user-attachments/assets/e0f42934-3e45-499b-8fb0-4f3a37e0c93a" />
---
## Known Issues
- When toggling Dark Mode through Settings and returning to the Comic screen, thumbnails may temporarily appear blank.  
  **Cause:** The RecyclerView adapter cache is cleared before image reload after a configuration change.  
  **Planned fix:** Re-initialize the thumbnail cache in `onResume()` or persist it via ViewModel scope.

---

## 🛠️ Installation

### 1. Clone the repository
```bash
git clone https://github.com/marlon373/comic-reader-java.git
