import sqlite3
import os

db_path = r'c:\medical_appointment_system\instance\medical_appointment.db'

def migrate():
    if not os.path.exists(db_path):
        print("Database not found.")
        return

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    print("Migrating medical_report table...")
    try:
        cursor.execute("ALTER TABLE medical_report ADD COLUMN file_path VARCHAR(500)")
        cursor.execute("ALTER TABLE medical_report ADD COLUMN category VARCHAR(50) DEFAULT 'Other'")
        print("Added file_path and category to medical_report")
    except sqlite3.OperationalError as e:
        print(f"MedicalReport migration skipped or error: {e}")

    print("Creating RecordShare table...")
    try:
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS record_share (
                id INTEGER PRIMARY KEY,
                report_id INTEGER NOT NULL,
                doctor_id INTEGER NOT NULL,
                shared_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(report_id) REFERENCES medical_report(id),
                FOREIGN KEY(doctor_id) REFERENCES user(id)
            )
        ''')
        print("Created record_share table")
    except sqlite3.OperationalError as e:
        print(f"RecordShare table error: {e}")

    print("Creating AuditTrail table...")
    try:
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS audit_trail (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                action VARCHAR(50) NOT NULL,
                target_type VARCHAR(50),
                target_id INTEGER,
                details TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(user_id) REFERENCES user(id)
            )
        ''')
        print("Created audit_trail table")
    except sqlite3.OperationalError as e:
        print(f"AuditTrail table error: {e}")

    print("Creating Notification table...")
    try:
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS notification (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL,
                type VARCHAR(50),
                message TEXT NOT NULL,
                is_read BOOLEAN DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(user_id) REFERENCES user(id)
            )
        ''')
        print("Created notification table")
    except sqlite3.OperationalError as e:
        print(f"Notification table error: {e}")

    conn.commit()
    conn.close()
    print("Migration complete!")

if __name__ == '__main__':
    migrate()
