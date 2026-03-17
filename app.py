from flask import Flask, render_template, request, redirect, url_for, session, flash, jsonify
from werkzeug.utils import secure_filename
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime
import os
import razorpay
import hmac
import hashlib
from fpdf import FPDF
import io
from flask import send_file

app = Flask(__name__)
app.config['SECRET_KEY'] = 'medical-system-secret-key-2024'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///medical_appointment.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['UPLOAD_FOLDER'] = os.path.join('static', 'uploads')
app.config['ALLOWED_EXTENSIONS'] = {'pdf', 'png', 'jpg', 'jpeg', 'doc', 'docx'}
db = SQLAlchemy(app)

if not os.path.exists(app.config['UPLOAD_FOLDER']):
    os.makedirs(app.config['UPLOAD_FOLDER'])

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

# Razorpay Configuration (Update with your actual keys from Razorpay Dashboard)
RAZORPAY_KEY_ID = 'rzp_test_YOUR_KEY_ID'
RAZORPAY_KEY_SECRET = 'YOUR_SECRET_KEY'
razorpay_client = razorpay.Client(auth=(RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET))

# Database Models
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(100), unique=True, nullable=False)
    password = db.Column(db.String(200), nullable=False)
    role = db.Column(db.String(20), nullable=False)
    name = db.Column(db.String(100), nullable=False)
    specialization = db.Column(db.String(100))
    qualification = db.Column(db.String(200))
    experience = db.Column(db.String(50))
    phone = db.Column(db.String(20))
    address = db.Column(db.String(500))
    is_approved = db.Column(db.Boolean, default=False)
    consultation_fee = db.Column(db.Float, default=0.0)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    def __repr__(self):
        return f'<User {self.email} - {self.role}>'

class Appointment(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    doctor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    date = db.Column(db.String(50), nullable=False)
    time = db.Column(db.String(50), nullable=False)
    status = db.Column(db.String(20), default='pending')
    symptoms = db.Column(db.Text)
    notes = db.Column(db.Text)
    
    # Payment fields
    payment_status = db.Column(db.String(20), default='pending') # pending, paid, waived
    payment_method = db.Column(db.String(50)) # online, offline
    amount = db.Column(db.Float)
    
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    patient = db.relationship('User', foreign_keys=[patient_id], backref='appointments_as_patient')
    doctor = db.relationship('User', foreign_keys=[doctor_id], backref='appointments_as_doctor')

class Payment(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    appointment_id = db.Column(db.Integer, db.ForeignKey('appointment.id'), nullable=False)
    amount = db.Column(db.Float, nullable=False)
    transaction_id = db.Column(db.String(100))
    payment_method = db.Column(db.String(50))
    status = db.Column(db.String(20))
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    appointment = db.relationship('Appointment', backref=db.backref('payments', lazy=True))

class CurrentDoctor(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    doctor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    appointment_id = db.Column(db.Integer, db.ForeignKey('appointment.id'))
    assigned_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    patient = db.relationship('User', foreign_keys=[patient_id])
    doctor = db.relationship('User', foreign_keys=[doctor_id])
    appointment = db.relationship('Appointment')

class Prescription(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    doctor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    current_doctor_id = db.Column(db.Integer, db.ForeignKey('current_doctor.id'))
    medicines = db.Column(db.Text, nullable=False)
    dosage = db.Column(db.Text, nullable=False)
    instructions = db.Column(db.Text)
    notes = db.Column(db.Text)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    patient = db.relationship('User', foreign_keys=[patient_id])
    doctor = db.relationship('User', foreign_keys=[doctor_id])
    current_doctor = db.relationship('CurrentDoctor')

class MedicalReport(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    report_name = db.Column(db.String(200), nullable=False)
    report_type = db.Column(db.String(100))
    report_date = db.Column(db.String(50))
    doctor_name = db.Column(db.String(100))
    description = db.Column(db.Text)
    file_path = db.Column(db.String(500))
    category = db.Column(db.String(50), default='Other') # Prescription, Test Report, Diagnosis, Other
    uploaded_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    patient = db.relationship('User', backref='medical_reports')

class RecordShare(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    report_id = db.Column(db.Integer, db.ForeignKey('medical_report.id'), nullable=False)
    doctor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    shared_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    report = db.relationship('MedicalReport', backref='shares')
    doctor = db.relationship('User', backref='shared_reports')

class AuditTrail(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    action = db.Column(db.String(50), nullable=False) # Upload, Delete, Update, Share
    target_type = db.Column(db.String(50)) # MedicalReport
    target_id = db.Column(db.Integer)
    details = db.Column(db.Text)
    timestamp = db.Column(db.DateTime, default=datetime.utcnow)

class Notification(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    type = db.Column(db.String(50)) # appointment, refill, checkup
    message = db.Column(db.Text, nullable=False)
    is_read = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

# Create tables and admin user
with app.app_context():
    db.create_all()
    # Create admin user if not exists
    admin = User.query.filter_by(email='admin@system.com').first()
    if not admin:
        admin = User(
            email='admin@system.com',
            password=generate_password_hash('admin123'),
            role='admin',
            name='Administrator',
            phone='0000000000',
            address='System Address',
            is_approved=True
        )
        db.session.add(admin)
        db.session.commit()

# ============ ROUTES ============

@app.route('/')
def home():
    return render_template('home.html')

@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        email = request.form['email']
        password = request.form['password']
        confirm_password = request.form['confirm_password']
        name = request.form['name']
        role = request.form['role']
        phone = request.form['phone']
        address = request.form['address']
        
        if password != confirm_password:
            flash('Passwords do not match!', 'error')
            return redirect(url_for('register'))
        
        if User.query.filter_by(email=email).first():
            flash('Email already registered!', 'error')
            return redirect(url_for('register'))
        
        specialization = request.form.get('specialization', '')
        qualification = request.form.get('qualification', '')
        experience = request.form.get('experience', '')
        
        user = User(
            email=email,
            password=generate_password_hash(password),
            name=name,
            role=role,
            specialization=specialization if role == 'doctor' else None,
            qualification=qualification if role == 'doctor' else None,
            experience=experience if role == 'doctor' else None,
            phone=phone,
            address=address,
            is_approved=True if role != 'doctor' else False,
            consultation_fee=float(request.form.get('consultation_fee', 0.0)) if role == 'doctor' else 0.0
        )
        
        db.session.add(user)
        db.session.commit()
        
        if role == 'doctor':
            flash('Registration successful! Please wait for admin approval.', 'success')
        else:
            flash('Registration successful! Please login.', 'success')
        
        return redirect(url_for('login'))
    
    return render_template('register.html')

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        email = request.form['email']
        password = request.form['password']
        
        user = User.query.filter_by(email=email).first()
        
        if user and check_password_hash(user.password, password):
            if user.role == 'doctor' and not user.is_approved:
                flash('Your account is pending approval from admin.', 'warning')
                return redirect(url_for('login'))
            
            session['user_id'] = user.id
            session['role'] = user.role
            session['name'] = user.name
            session['email'] = user.email
            
            if user.role == 'patient':
                return redirect(url_for('patient_dashboard'))
            elif user.role == 'doctor':
                return redirect(url_for('doctor_dashboard'))
            elif user.role == 'admin':
                return redirect(url_for('admin_dashboard'))
        
        flash('Invalid email or password!', 'error')
    
    return render_template('login.html')

    return render_template('login.html')

@app.route('/forgot-password', methods=['GET', 'POST'])
def forgot_password():
    if request.method == 'POST':
        email = request.form.get('email')
        user = User.query.filter_by(email=email).first()
        if user:
            # In a real app, send actual email with token
            flash(f'A password reset link has been sent to {email} (Mocked)', 'success')
        else:
            flash('If that email exists, a reset link was sent.', 'info')
        return redirect(url_for('login'))
    return render_template('forgot_password.html')

@app.route('/reset-password/<token>', methods=['GET', 'POST'])
def reset_password(token):
    # Mocking token check
    if request.method == 'POST':
        new_password = request.form.get('password')
        # Here we would identify user from token
        flash('Password reset successful! Please login.', 'success')
        return redirect(url_for('login'))
    return render_template('reset_password.html', token=token)

@app.route('/logout')
def logout():
    session.clear()
    flash('Logged out successfully!', 'success')
    return redirect(url_for('home'))

@app.context_processor
def inject_notification_count():
    unread_count = 0
    if 'user_id' in session:
        unread_count = Notification.query.filter_by(user_id=session['user_id'], is_read=False).count()
    return dict(unread_count=unread_count)

# ============ NOTIFICATION LOGIC ============

def check_and_generate_notifications(user_id):
    # 1. Appointment Reminders (Check for appointments tomorrow)
    from datetime import timedelta
    tomorrow = (datetime.utcnow() + timedelta(days=1)).strftime('%Y-%m-%d')
    appointments = Appointment.query.filter_by(patient_id=user_id, date=tomorrow, status='approved').all()
    for appt in appointments:
        message = f"Reminder: Upcoming appointment with Dr. {appt.doctor.name} tomorrow at {appt.time}."
        existing = Notification.query.filter_by(user_id=user_id, message=message).first()
        if not existing:
            notif = Notification(user_id=user_id, type='appointment', message=message)
            db.session.add(notif)
            
    # 2. Prescription Refill Reminders (30 days after prescription)
    thirty_days_ago = datetime.utcnow() - timedelta(days=30)
    prescriptions = Prescription.query.filter(Prescription.patient_id == user_id, Prescription.created_at <= thirty_days_ago).all()
    for pres in prescriptions:
        message = f"Reminder: Your prescription from Dr. {pres.doctor.name} may need a refill soon."
        existing = Notification.query.filter_by(user_id=user_id, message=message).first()
        if not existing:
            notif = Notification(user_id=user_id, type='refill', message=message)
            db.session.add(notif)
            
    # 3. Health Checkup Reminders (6 months after last appointment)
    six_months_ago = datetime.utcnow() - timedelta(days=180)
    last_appt = Appointment.query.filter_by(patient_id=user_id, status='approved').order_by(Appointment.date.desc()).first()
    if last_appt:
        try:
            appt_date = datetime.strptime(last_appt.date, '%Y-%m-%d')
            if appt_date <= six_months_ago:
                message = "It's been 6 months since your last appointment. Consider booking a general health checkup."
                existing = Notification.query.filter_by(user_id=user_id, message=message).first()
                if not existing:
                    notif = Notification(user_id=user_id, type='checkup', message=message)
                    db.session.add(notif)
        except:
            pass
            
    db.session.commit()

# ============ PATIENT ROUTES ============

@app.route('/patient/dashboard')
def patient_dashboard():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
    
    patient_id = session['user_id']
    check_and_generate_notifications(patient_id)
    
    current_doctors = CurrentDoctor.query.filter_by(patient_id=patient_id).all()
    appointments = Appointment.query.filter_by(patient_id=patient_id).order_by(Appointment.created_at.desc()).limit(5).all()
    notifications = Notification.query.filter_by(user_id=patient_id, is_read=False).order_by(Notification.created_at.desc()).all()
    prescription_count = Prescription.query.filter_by(patient_id=patient_id).count()
    
    return render_template('patient_dashboard.html', 
                         current_doctors=current_doctors,
                         appointments=appointments,
                         notifications=notifications,
                         prescription_count=prescription_count,
                         name=session['name'])

@app.route('/patient/notifications')
def patient_notifications():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
        
    notifications = Notification.query.filter_by(user_id=session['user_id']).order_by(Notification.created_at.desc()).all()
    return render_template('notifications.html', notifications=notifications, name=session['name'])

@app.route('/patient/medical-reports')
def medical_reports():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
    
    patient_id = session['user_id']
    reports = MedicalReport.query.filter_by(patient_id=patient_id).all()
    doctors = User.query.filter_by(role='doctor', is_approved=True).all()
    
    return render_template('medical_reports.html', reports=reports, doctors=doctors, name=session['name'])

@app.route('/patient/medical-reports/upload', methods=['POST'])
def upload_medical_report():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
        
    if 'report_file' not in request.files:
        flash('No file part', 'error')
        return redirect(url_for('medical_reports'))
        
    file = request.files['report_file']
    if file.filename == '':
        flash('No selected file', 'error')
        return redirect(url_for('medical_reports'))
        
    if file and allowed_file(file.filename):
        filename = secure_filename(f"{session['user_id']}_{datetime.utcnow().timestamp()}_{file.filename}")
        file_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(file_path)
        
        report = MedicalReport(
            patient_id=session['user_id'],
            report_name=request.form['report_name'],
            report_type=request.form['category'], # using report_type as legacy but category is better
            category=request.form['category'],
            report_date=request.form['report_date'],
            doctor_name=request.form.get('doctor_name', ''),
            description=request.form.get('description', ''),
            file_path=filename
        )
        db.session.add(report)
        db.session.commit()
        
        # Log to Audit Trail
        audit = AuditTrail(
            user_id=session['user_id'],
            action='Upload',
            target_type='MedicalReport',
            target_id=report.id,
            details=f"Uploaded {report.report_name} in category {report.category}"
        )
        db.session.add(audit)
        db.session.commit()
        
        flash('Medical report uploaded successfully!', 'success')
    else:
        flash('File type not allowed', 'error')
        
    return redirect(url_for('medical_reports'))

@app.route('/patient/medical-reports/delete/<int:report_id>', methods=['POST'])
def delete_medical_report(report_id):
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
        
    report = MedicalReport.query.get(report_id)
    if report and report.patient_id == session['user_id']:
        # Delete file from disk
        if report.file_path:
            try:
                os.remove(os.path.join(app.config['UPLOAD_FOLDER'], report.file_path))
            except:
                pass
                
        # Log to Audit Trail before deleting
        audit = AuditTrail(
            user_id=session['user_id'],
            action='Delete',
            target_type='MedicalReport',
            target_id=report.id,
            details=f"Deleted {report.report_name}"
        )
        db.session.add(audit)
        
        # Delete all shares
        RecordShare.query.filter_by(report_id=report.id).delete()
        
        db.session.delete(report)
        db.session.commit()
        return jsonify({'success': True, 'message': 'Report deleted successfully!'})
        
    return jsonify({'error': 'Report not found or unauthorized'}), 404

@app.route('/patient/medical-reports/share', methods=['POST'])
def share_medical_report():
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
        
    data = request.get_json()
    report_id = data.get('report_id')
    doctor_id = data.get('doctor_id')
    
    report = MedicalReport.query.get(report_id)
    doctor = User.query.get(doctor_id)
    
    if report and report.patient_id == session['user_id'] and doctor and doctor.role == 'doctor':
        existing = RecordShare.query.filter_by(report_id=report_id, doctor_id=doctor_id).first()
        if not existing:
            share = RecordShare(report_id=report_id, doctor_id=doctor_id)
            db.session.add(share)
            
            # Log to Audit Trail
            audit = AuditTrail(
                user_id=session['user_id'],
                action='Share',
                target_type='MedicalReport',
                target_id=report_id,
                details=f"Shared {report.report_name} with Dr. {doctor.name}"
            )
            db.session.add(audit)
            db.session.commit()
            return jsonify({'success': True, 'message': f'Report shared with Dr. {doctor.name}!'})
        else:
            return jsonify({'error': 'Already shared with this doctor'}), 400
            
    return jsonify({'error': 'Invalid request'}), 400

    return jsonify({'error': 'Invalid request'}), 400

@app.route('/patient/medical-reports/edit/<int:report_id>', methods=['POST'])
def edit_medical_report(report_id):
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
        
    report = MedicalReport.query.get(report_id)
    if not report or report.patient_id != session['user_id']:
        return jsonify({'error': 'Report not found'}), 404
        
    data = request.get_json()
    report.report_name = data.get('report_name', report.report_name)
    report.category = data.get('category', report.category)
    report.report_date = data.get('report_date', report.report_date)
    report.description = data.get('description', report.description)
    
    # Log to Audit Trail
    audit = AuditTrail(
        user_id=session['user_id'],
        action='Update',
        target_type='MedicalReport',
        target_id=report.id,
        details=f"Updated details for {report.report_name}"
    )
    db.session.add(audit)
    db.session.commit()
    
    return jsonify({'success': True, 'message': 'Report updated successfully!'})

@app.route('/doctor/patient-records/<int:patient_id>')
def doctor_patient_records(patient_id):
    if 'user_id' not in session or session['role'] != 'doctor':
        return redirect(url_for('login'))
        
    doctor_id = session['user_id']
    patient = User.query.get(patient_id)
    
    # Only show reports shared with this specific doctor
    shared_reports = db.session.query(MedicalReport).join(RecordShare).filter(
        RecordShare.doctor_id == doctor_id,
        MedicalReport.patient_id == patient_id
    ).all()
    
    return render_template('doctor_patient_records.html', patient=patient, reports=shared_reports, name=session['name'])

@app.route('/patient/appointment', methods=['GET', 'POST'])
def appointment():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
    
    if request.method == 'POST':
        doctor_id = request.form['doctor_id']
        date = request.form['date']
        time = request.form['time']
        symptoms = request.form['symptoms']
        notes = request.form.get('notes', '')
        
        doctor = User.query.get(doctor_id)
        amount = doctor.consultation_fee if doctor else 0.0

        appointment = Appointment(
            patient_id=session['user_id'],
            doctor_id=doctor_id,
            date=date,
            time=time,
            symptoms=symptoms,
            notes=notes,
            payment_status='pending',
            payment_method='offline',
            amount=amount
        )
        
        db.session.add(appointment)
        db.session.commit()
        
        flash('Appointment request sent successfully! Please pay at clinic.', 'success')
        return redirect(url_for('patient_dashboard'))
    
    doctors = User.query.filter_by(role='doctor', is_approved=True).all()
    return render_template('appointment.html', doctors=doctors, name=session['name'])

@app.route('/patient/current-doctor')
def current_doctor():
    if 'user_id' not in session or session['role'] != 'patient':
        return redirect(url_for('login'))
    
    patient_id = session['user_id']
    current_doctors = CurrentDoctor.query.filter_by(patient_id=patient_id).all()
    
    for cd in current_doctors:
        cd.prescriptions = Prescription.query.filter_by(
            patient_id=patient_id, 
            doctor_id=cd.doctor_id
        ).order_by(Prescription.created_at.desc()).all()
    
    return render_template('current_doctor.html', current_doctors=current_doctors, name=session['name'])

# ============ DOCTOR ROUTES ============

@app.route('/doctor/dashboard')
def doctor_dashboard():
    if 'user_id' not in session or session['role'] != 'doctor':
        return redirect(url_for('login'))
    
    doctor_id = session['user_id']
    
    pending_appointments = Appointment.query.filter_by(
        doctor_id=doctor_id, 
        status='pending'
    ).order_by(Appointment.created_at.desc()).all()
    
    current_patients = CurrentDoctor.query.filter_by(doctor_id=doctor_id).all()
    
    approved_appointments = Appointment.query.filter_by(
        doctor_id=doctor_id, 
        status='approved'
    ).order_by(Appointment.created_at.desc()).limit(5).all()
    
    return render_template('doctor_dashboard.html',
                         pending_appointments=pending_appointments,
                         current_patients=current_patients,
                         approved_appointments=approved_appointments,
                         name=session['name'])

@app.route('/doctor/prescription/<int:patient_id>', methods=['GET', 'POST'])
def doctor_prescription(patient_id):
    if 'user_id' not in session or session['role'] != 'doctor':
        return redirect(url_for('login'))
    
    doctor_id = session['user_id']
    current_doctor = CurrentDoctor.query.filter_by(
        patient_id=patient_id,
        doctor_id=doctor_id
    ).first()
    
    if not current_doctor:
        flash('You are not authorized to write prescription for this patient!', 'error')
        return redirect(url_for('doctor_dashboard'))
    
    patient = User.query.get(patient_id)
    
    if request.method == 'POST':
        medicines = request.form['medicines']
        dosage = request.form['dosage']
        instructions = request.form['instructions']
        notes = request.form.get('notes', '')
        
        prescription = Prescription(
            patient_id=patient_id,
            doctor_id=doctor_id,
            current_doctor_id=current_doctor.id,
            medicines=medicines,
            dosage=dosage,
            instructions=instructions,
            notes=notes
        )
        
        db.session.add(prescription)
        db.session.commit()
        
        # Notify patient about the new prescription
        notif = Notification(
            user_id=patient_id,
            message=f"A new prescription has been issued by Dr. {session['name']}. You can view it in your dashboard.",
            type='general'
        )
        db.session.add(notif)
        db.session.commit()
        
        flash('Prescription created successfully!', 'success')
        return redirect(url_for('doctor_dashboard'))
    
    return render_template('prescription.html', patient=patient, name=session['name'])

# ============ ADMIN ROUTES ============

@app.route('/admin/dashboard')
def admin_dashboard():
    if 'user_id' not in session or session['role'] != 'admin':
        return redirect(url_for('login'))
    
    pending_doctors = User.query.filter_by(role='doctor', is_approved=False).all()
    all_doctors = User.query.filter_by(role='doctor', is_approved=True).all()
    all_patients = User.query.filter_by(role='patient').all()
    
    stats = {
        'total_patients': len(all_patients),
        'total_doctors': len(all_doctors),
        'pending_approvals': len(pending_doctors),
        'total_appointments': Appointment.query.count()
    }
    
    return render_template('admin_dashboard.html',
                         pending_doctors=pending_doctors,
                         all_doctors=all_doctors,
                         all_patients=all_patients,
                         stats=stats,
                         name=session['name'])

# ============ API ROUTES ============

@app.route('/api/approve-doctor/<int:doctor_id>', methods=['POST'])
def approve_doctor(doctor_id):
    if 'user_id' not in session or session['role'] != 'admin':
        return jsonify({'error': 'Unauthorized'}), 401
    
    doctor = User.query.get(doctor_id)
    if doctor and doctor.role == 'doctor':
        doctor.is_approved = True
        db.session.commit()
        return jsonify({'success': True, 'message': 'Doctor approved successfully!'})
    
    return jsonify({'error': 'Doctor not found'}), 404

@app.route('/api/reject-doctor/<int:doctor_id>', methods=['POST'])
def reject_doctor(doctor_id):
    if 'user_id' not in session or session['role'] != 'admin':
        return jsonify({'error': 'Unauthorized'}), 401
    
    doctor = User.query.get(doctor_id)
    if doctor and doctor.role == 'doctor':
        db.session.delete(doctor)
        db.session.commit()
        return jsonify({'success': True, 'message': 'Doctor rejected and removed!'})
    
    return jsonify({'error': 'Doctor not found'}), 404

@app.route('/api/delete-doctor/<int:doctor_id>', methods=['DELETE', 'POST'])
def delete_doctor(doctor_id):
    if 'user_id' not in session or session['role'] != 'admin':
        return jsonify({'error': 'Unauthorized'}), 401
        
    doctor = User.query.get(doctor_id)
    if doctor and doctor.role == 'doctor':
        # Optional: Add checks if doctor has active appointments or dependencies
        # For simplicity, we'll delete the doctor and their related records or handle it gracefully
        # In a real app, you might want to soft-delete or check for dependencies
        
        db.session.delete(doctor)
        db.session.commit()
        return jsonify({'success': True, 'message': 'Doctor deleted successfully!'})
        
    return jsonify({'error': 'Doctor not found'}), 404

@app.route('/api/update-doctor-fee/<int:doctor_id>', methods=['POST'])
def update_doctor_fee(doctor_id):
    if 'user_id' not in session or session['role'] != 'admin':
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.get_json()
    new_fee = data.get('consultation_fee')
    
    if new_fee is None:
        return jsonify({'error': 'Consultation fee is required'}), 400
        
    try:
        new_fee = float(new_fee)
    except ValueError:
        return jsonify({'error': 'Invalid fee format'}), 400
        
    doctor = User.query.get(doctor_id)
    if doctor and doctor.role == 'doctor':
        doctor.consultation_fee = new_fee
        db.session.commit()
        return jsonify({'success': True, 'message': f'Consultation fee updated to ₹{new_fee} for Dr. {doctor.name}'})
    
    return jsonify({'error': 'Doctor not found'}), 404

@app.route('/api/approve-appointment/<int:appointment_id>', methods=['POST'])
def approve_appointment(appointment_id):
    if 'user_id' not in session or session['role'] != 'doctor':
        return jsonify({'error': 'Unauthorized'}), 401
    
    appointment = Appointment.query.get(appointment_id)
    if appointment and appointment.doctor_id == session['user_id']:
        appointment.status = 'approved'
        
        existing = CurrentDoctor.query.filter_by(
            patient_id=appointment.patient_id,
            doctor_id=appointment.doctor_id
        ).first()
        
        if not existing:
            current_doctor = CurrentDoctor(
                patient_id=appointment.patient_id,
                doctor_id=appointment.doctor_id,
                appointment_id=appointment.id
            )
            db.session.add(current_doctor)
        
        db.session.commit()
        return jsonify({'success': True, 'message': 'Appointment approved!'})
    
    return jsonify({'error': 'Appointment not found'}), 404

@app.route('/api/reject-appointment/<int:appointment_id>', methods=['POST'])
def reject_appointment(appointment_id):
    if 'user_id' not in session or session['role'] != 'doctor':
        return jsonify({'error': 'Unauthorized'}), 401
    
    appointment = Appointment.query.get(appointment_id)
    if appointment and appointment.doctor_id == session['user_id']:
        appointment.status = 'rejected'
        db.session.commit()
        return jsonify({'success': True, 'message': 'Appointment rejected!'})
    
    return jsonify({'error': 'Appointment not found'}), 404

@app.route('/api/get-prescription/<int:prescription_id>')
def get_prescription(prescription_id):
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
    
    prescription = Prescription.query.get(prescription_id)
    if prescription:
        user_id = session['user_id']
        user_role = session['role']
        
        if user_role == 'patient' and prescription.patient_id != user_id:
            return jsonify({'error': 'Unauthorized'}), 403
        elif user_role == 'doctor' and prescription.doctor_id != user_id:
            return jsonify({'error': 'Unauthorized'}), 403
        
        return jsonify({
            'success': True,
            'prescription': {
                'id': prescription.id,
                'patient_name': prescription.patient.name,
                'doctor_name': prescription.doctor.name,
                'medicines': prescription.medicines,
                'dosage': prescription.dosage,
                'instructions': prescription.instructions,
                'notes': prescription.notes,
                'created_at': prescription.created_at.strftime('%Y-%m-%d %H:%M:%S')
            }
        })
    
    return jsonify({'error': 'Prescription not found'}), 404

@app.route('/api/remove-current-doctor/<int:current_doctor_id>', methods=['POST'])
def remove_current_doctor(current_doctor_id):
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
    
    current_doctor = CurrentDoctor.query.get(current_doctor_id)
    if current_doctor and current_doctor.patient_id == session['user_id']:
        db.session.delete(current_doctor)
        db.session.commit()
        return jsonify({'success': True, 'message': 'Doctor removed from current doctors!'})
    
    return jsonify({'error': 'Not found or unauthorized'}), 404

@app.route('/api/mark-notification-read/<int:notif_id>', methods=['POST'])
def mark_notification_read(notif_id):
    if 'user_id' not in session:
        return jsonify({'error': 'Unauthorized'}), 401
        
    notif = Notification.query.get(notif_id)
    if notif and notif.user_id == session['user_id']:
        notif.is_read = True
        db.session.commit()
        return jsonify({'success': True})
    return jsonify({'error': 'Notification not found'}), 404

@app.route('/api/send-notification', methods=['POST'])
def send_notification():
    if 'user_id' not in session or session['role'] != 'admin':
        return jsonify({'error': 'Unauthorized'}), 401
        
    data = request.get_json()
    user_id = data.get('user_id')
    message = data.get('message')
    notif_type = data.get('type', 'general')
    
    if not user_id or not message:
        return jsonify({'error': 'Patient ID and message are required'}), 400
        
    user = User.query.get(user_id)
    if not user:
        return jsonify({'error': 'Patient not found'}), 404
        
    notification = Notification(
        user_id=user_id,
        message=message,
        type=notif_type
    )
    db.session.add(notification)
    db.session.commit()
    
    return jsonify({'success': True, 'message': 'Notification sent successfully!'})

@app.route('/patient/prescription/download/<int:prescription_id>')
def download_prescription_pdf(prescription_id):
    if 'user_id' not in session:
        return redirect(url_for('login'))
        
    prescription = Prescription.query.get_or_404(prescription_id)
    
    # Check authorization
    if session['role'] == 'patient' and prescription.patient_id != session['user_id']:
        flash('Unauthorized access', 'error')
        return redirect(url_for('patient_dashboard'))
    elif session['role'] == 'doctor' and prescription.doctor_id != session['user_id']:
        flash('Unauthorized access', 'error')
        return redirect(url_for('doctor_dashboard'))

    # Create PDF
    pdf = FPDF()
    pdf.add_page()
    
    # Header
    pdf.set_font('Arial', 'B', 20)
    pdf.cell(0, 10, 'MEDICAL PRESCRIPTION', ln=True, align='C')
    pdf.ln(10)
    
    # Doctor & Patient Info
    pdf.set_font('Arial', 'B', 12)
    pdf.cell(100, 10, f"Doctor: Dr. {prescription.doctor.name}", ln=False)
    pdf.cell(0, 10, f"Date: {prescription.created_at.strftime('%Y-%m-%d')}", ln=True, align='R')
    pdf.cell(0, 10, f"Patient: {prescription.patient.name}", ln=True)
    pdf.ln(5)
    pdf.line(10, pdf.get_y(), 200, pdf.get_y())
    pdf.ln(10)
    
    # Medicines
    pdf.set_font('Arial', 'B', 14)
    pdf.set_text_color(52, 152, 219) # #3498db
    pdf.cell(0, 10, 'Medicines & Dosage', ln=True)
    pdf.set_font('Arial', '', 12)
    pdf.set_text_color(0, 0, 0)
    
    pdf.multi_cell(0, 8, f"Medicines:\n{prescription.medicines}")
    pdf.ln(5)
    pdf.multi_cell(0, 8, f"Dosage:\n{prescription.dosage}")
    pdf.ln(10)
    
    # Instructions
    pdf.set_font('Arial', 'B', 14)
    pdf.set_text_color(52, 152, 219)
    pdf.cell(0, 10, 'Instructions', ln=True)
    pdf.set_font('Arial', '', 12)
    pdf.set_text_color(0, 0, 0)
    pdf.multi_cell(0, 8, prescription.instructions or "No specific instructions provided.")
    pdf.ln(10)
    
    # Notes
    if prescription.notes:
        pdf.set_font('Arial', 'B', 14)
        pdf.set_text_color(52, 152, 219)
        pdf.cell(0, 10, 'Additional Notes', ln=True)
        pdf.set_font('Arial', '', 12)
        pdf.set_text_color(0, 0, 0)
        pdf.multi_cell(0, 8, prescription.notes)
        pdf.ln(20)
        
    # Footer
    pdf.set_y(-30)
    pdf.set_font('Arial', 'I', 10)
    pdf.cell(0, 10, f"Generated by MedAppoint System on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", align='C')
    
    # Output to buffer
    output = io.BytesIO()
    pdf_content = pdf.output(dest='S')
    if isinstance(pdf_content, str):
        output.write(pdf_content.encode('latin1'))
    else:
        output.write(pdf_content)
    output.seek(0)
    
    return send_file(
        output,
        as_attachment=True,
        download_name=f"prescription_{prescription_id}.pdf",
        mimetype='application/pdf'
    )
        
    return jsonify({'error': 'Notification not found'}), 404

# ============ RAZORPAY API ROUTES ============

@app.route('/api/create-razorpay-order', methods=['POST'])
def create_razorpay_order():
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
    
    data = request.get_json()
    doctor_id = data.get('doctor_id')
    doctor = User.query.get(doctor_id)
    
    if not doctor:
        return jsonify({'error': 'Doctor not found'}), 404
        
    amount = int(doctor.consultation_fee * 100) # Amount in paise
    
    try:
        order_data = {
            'amount': amount,
            'currency': 'INR',
            'payment_capture': 1
        }
        order = razorpay_client.order.create(data=order_data)
        return jsonify({
            'success': True,
            'order_id': order['id'],
            'amount': order['amount'],
            'key': RAZORPAY_KEY_ID
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/verify-razorpay-payment', methods=['POST'])
def verify_razorpay_payment():
    if 'user_id' not in session or session['role'] != 'patient':
        return jsonify({'error': 'Unauthorized'}), 401
        
    data = request.get_json()
    razorpay_order_id = data.get('razorpay_order_id')
    razorpay_payment_id = data.get('razorpay_payment_id')
    razorpay_signature = data.get('razorpay_signature')
    
    appointment_data = data.get('appointment_data')
    
    # Verify signature
    params_dict = {
        'razorpay_order_id': razorpay_order_id,
        'razorpay_payment_id': razorpay_payment_id,
        'razorpay_signature': razorpay_signature
    }
    
    try:
        razorpay_client.utility.verify_payment_signature(params_dict)
        
        # Payment verified, create appointment
        doctor_id = appointment_data.get('doctor_id')
        doctor = User.query.get(doctor_id)
        
        appointment = Appointment(
            patient_id=session['user_id'],
            doctor_id=doctor_id,
            date=appointment_data.get('date'),
            time=appointment_data.get('time'),
            symptoms=appointment_data.get('symptoms'),
            notes=appointment_data.get('notes', ''),
            payment_status='paid',
            payment_method='online',
            amount=doctor.consultation_fee
        )
        
        db.session.add(appointment)
        db.session.commit()
        
        payment = Payment(
            appointment_id=appointment.id,
            amount=doctor.consultation_fee,
            transaction_id=razorpay_payment_id,
            payment_method='online',
            status='success'
        )
        db.session.add(payment)
        db.session.commit()
        
        return jsonify({'success': True, 'message': 'Payment verified and appointment booked!'})
        
    except Exception as e:
        return jsonify({'error': 'Payment verification failed'}), 400

# ============ RUN APPLICATION ============

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, port=5000)