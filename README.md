# 🏥 Medical Appointment & Health Management System 🩺

![Flask](https://img.shields.io/badge/flask-%23000.svg?style=for-the-badge&logo=flask&logoColor=white)
![SQLite](https://img.shields.io/badge/sqlite-%2307405e.svg?style=for-the-badge&logo=sqlite&logoColor=white)
![Bootstrap](https://img.shields.io/badge/bootstrap-%238511FA.svg?style=for-the-badge&logo=bootstrap&logoColor=white)
![Razorpay](https://img.shields.io/badge/Razorpay-020425?style=for-the-badge&logo=razorpay&logoColor=3399FF)

> A modern, full-stack medical appointment system designed for patients and doctors to manage health records, appointments, and prescriptions seamlessly.

---

## 🌟 Key Features

### 👤 Patient Portal
- **Dashboard**: Overview of upcoming appointments and health status.
- **Appointment Booking**: Real-time booking with specialized doctors.
- **Secure Payments**: Online payment gateway integration (Razorpay) or offline options.
- **Medical Records**: Upload, categorize (Prescription, Test Report, etc.), and share documents with doctors.
- **PDF Prescriptions**: View and download digital prescriptions issued by doctors.
- **Smart Notifications**: Reminders for appointments, medicine refills, and regular checkups.

### 👨‍⚕️ Doctor Portal
- **Patient Management**: Access shared medical records only when authorized by the patient.
- **Appointment Queue**: Manage pending and approved appointments effortlessly.
- **Digital Prescriptions**: Issue formatted prescriptions directly from the dashboard.

### 🔑 Admin Panel
- **User Moderation**: Approve or reject doctor registrations.
- **System Statistics**: Monitor the total number of patients, doctors, and appointments.
- **Audit Trails**: Complete log of medical record actions for security.

---

## 🚀 Getting Started

### Prerequisites
- Python 3.8+
- Virtual Environment (`venv`)

### Installation & Run

1. **Clone/Navigate to the directory**:
   ```bash
   cd medical_appointment_system
   ```

2. **Activate the environment**:
   ```powershell
   # Windows
   .\venv\Scripts\activate
   # Linux/macOS
   source venv/bin/activate
   ```

3. **Install Dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

4. **Launch the Application**:
   ```bash
   python app.py
   ```

5. **Visit in Browser**:
   [http://127.0.0.1:5000](http://127.0.0.1:5000)

---

## 🛡️ Default Credentials (Admin)

| Role | Email | Password |
| :--- | :--- | :--- |
| **Administrator** | `admin@system.com` | `admin123` |

---

## 📂 Project Structure

```text
├── app.py              # Main Application Entry & Routes
├── models.py           # Database Schema (SQLAlchemy)
├── static/             # CSS, JS, and Uploaded Files
├── templates/          # HTML Templates (Jinja2)
├── requirements.txt    # Python Dependencies
└── instance/           # SQLite Database Storage
```

---

## 🛠️ Built With

- **Backend**: Flask (Python)
- **Database**: SQLite with SQLAlchemy ORM
- **Frontend**: HTML5, Vanilla CSS3, Bootstrap 5
- **Payment Gateway**: Razorpay API
- **PDF Generation**: FPDF

---

*Developed with ❤️ for better healthcare management.*
