-- ==============================================================================
-- NALAM NAGAR EMERGENCY DISPATCH & ROUTE OPTIMIZATION
-- Relational Database Schema & Seed Data (Compatible with SQLite & MySQL)
-- ==============================================================================

-- Drop tables in reverse foreign-key order
DROP TABLE IF EXISTS trips;
DROP TABLE IF EXISTS emergencies;
DROP TABLE IF EXISTS road_edges;
DROP TABLE IF EXISTS hospital_specialties;
DROP TABLE IF EXISTS hospitals;
DROP TABLE IF EXISTS ambulances;
DROP TABLE IF EXISTS locations;

-- ------------------------------------------------------------------------------
-- 1. Locations (12 Town Zones)
-- ------------------------------------------------------------------------------
CREATE TABLE locations (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL
);

-- ------------------------------------------------------------------------------
-- 2. Ambulance Fleet (5 Units)
-- ------------------------------------------------------------------------------
CREATE TABLE ambulances (
    id VARCHAR(10) PRIMARY KEY,
    current_location_id VARCHAR(10) NOT NULL,
    is_available BOOLEAN NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    target_location_id VARCHAR(10),
    total_trips INT NOT NULL DEFAULT 0,
    FOREIGN KEY (current_location_id) REFERENCES locations(id),
    FOREIGN KEY (target_location_id) REFERENCES locations(id)
);

-- ------------------------------------------------------------------------------
-- 3. Hospitals (5 Receiving Medical Facilities)
-- ------------------------------------------------------------------------------
CREATE TABLE hospitals (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location_id VARCHAR(10) NOT NULL,
    total_beds INT NOT NULL,
    available_beds INT NOT NULL,
    FOREIGN KEY (location_id) REFERENCES locations(id)
);

-- ------------------------------------------------------------------------------
-- 4. Hospital Specialties (Many-to-Many / Capability Enum Matching)
-- ------------------------------------------------------------------------------
CREATE TABLE hospital_specialties (
    hospital_id VARCHAR(10) NOT NULL,
    specialty VARCHAR(50) NOT NULL,
    PRIMARY KEY (hospital_id, specialty),
    FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 5. Road Network Connections (16 Directed / Bidirectional Edges)
-- ------------------------------------------------------------------------------
CREATE TABLE road_edges (
    from_location_id VARCHAR(10) NOT NULL,
    to_location_id VARCHAR(10) NOT NULL,
    distance_km DOUBLE NOT NULL,
    travel_time_mins DOUBLE NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT 0,
    PRIMARY KEY (from_location_id, to_location_id),
    FOREIGN KEY (from_location_id) REFERENCES locations(id),
    FOREIGN KEY (to_location_id) REFERENCES locations(id)
);

-- ------------------------------------------------------------------------------
-- 6. Emergency Call Intake & Triage Ledger
-- ------------------------------------------------------------------------------
CREATE TABLE emergencies (
    id VARCHAR(20) PRIMARY KEY,
    patient_name VARCHAR(100) NOT NULL,
    location_id VARCHAR(10) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    condition_name VARCHAR(50) NOT NULL,
    required_capability VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_ambulance_id VARCHAR(10),
    assigned_hospital_id VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (assigned_ambulance_id) REFERENCES ambulances(id),
    FOREIGN KEY (assigned_hospital_id) REFERENCES hospitals(id)
);

-- ------------------------------------------------------------------------------
-- 7. Trip History & Audit Archive
-- ------------------------------------------------------------------------------
CREATE TABLE trips (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id VARCHAR(20),
    patient_name VARCHAR(100),
    ambulance_id VARCHAR(10) NOT NULL,
    hospital_id VARCHAR(10) NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    duration_mins DOUBLE,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    FOREIGN KEY (request_id) REFERENCES emergencies(id),
    FOREIGN KEY (ambulance_id) REFERENCES ambulances(id),
    FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);

-- ==============================================================================
-- SEED DATA
-- ==============================================================================

-- 1. Locations (12 Zones)
INSERT INTO locations (id, name, latitude, longitude) VALUES
('L1', 'Central Hospital Junction', 12.9716, 77.5946),
('L2', 'Market Circle', 12.9650, 77.6050),
('L3', 'Residential Colony A', 12.9800, 77.6100),
('L4', 'Industrial Area', 12.9550, 77.5800),
('L5', 'College Road', 12.9900, 77.6300),
('L6', 'Town Bus Terminal', 12.9750, 77.6000),
('L7', 'Greenfield Suburb', 12.9950, 77.5900),
('L8', 'Tech Park Zone', 12.9400, 77.6500),
('L9', 'Old City Bazaar', 12.9600, 77.5900),
('L10', 'Highway Bypass Junction', 12.9300, 77.6000),
('L11', 'Riverside Gardens', 12.9850, 77.5750),
('L12', 'North Gate Hub', 13.0100, 77.6150);

-- 2. Ambulances (5 Fleet Units)
INSERT INTO ambulances (id, current_location_id, is_available, status, total_trips) VALUES
('AMB-1', 'L1', 1, 'AVAILABLE', 0),
('AMB-2', 'L6', 1, 'AVAILABLE', 0),
('AMB-3', 'L4', 1, 'AVAILABLE', 0),
('AMB-4', 'L12', 1, 'AVAILABLE', 0),
('AMB-5', 'L2', 1, 'AVAILABLE', 0);

-- 3. Hospitals (5 Receiving Centers - 100% Initial Bed Availability)
INSERT INTO hospitals (id, name, location_id, total_beds, available_beds) VALUES
('H1', 'Nalam General Hospital', 'L1', 20, 20),
('H2', 'Apex Heart & Trauma Institute', 'L4', 15, 15),
('H3', 'Grace Children & Multi-Care', 'L3', 12, 12),
('H4', 'Sanjeevani Burn & Critical Care', 'L5', 18, 18),
('H5', 'Nalam Neuro & Stroke Care', 'L2', 15, 15);

-- 4. Hospital Specialties
INSERT INTO hospital_specialties (hospital_id, specialty) VALUES
('H1', 'ICU'),
('H1', 'TRAUMA_CENTRE'),
('H1', 'GENERAL_WARD'),
('H2', 'CARDIAC_CARE'),
('H2', 'TRAUMA_CENTRE'),
('H2', 'ICU'),
('H3', 'PEDIATRIC_ICU'),
('H3', 'GENERAL_WARD'),
('H3', 'ICU'),
('H4', 'BURN_UNIT'),
('H4', 'ICU'),
('H4', 'TRAUMA_CENTRE'),
('H5', 'STROKE_UNIT'),
('H5', 'ICU'),
('H5', 'CARDIAC_CARE');

-- 5. Road Network (Bidirectional Edges for 16 Corridors)
INSERT INTO road_edges (from_location_id, to_location_id, distance_km, travel_time_mins, is_closed) VALUES
-- L1 <-> L6
('L1', 'L6', 2.5, 6.0, 0),
('L6', 'L1', 2.5, 6.0, 0),
-- L6 <-> L2
('L6', 'L2', 3.0, 8.0, 0),
('L2', 'L6', 3.0, 8.0, 0),
-- L1 <-> L9
('L1', 'L9', 2.0, 5.0, 0),
('L9', 'L1', 2.0, 5.0, 0),
-- L9 <-> L4
('L9', 'L4', 4.0, 10.0, 0),
('L4', 'L9', 4.0, 10.0, 0),
-- L2 <-> L3
('L2', 'L3', 3.5, 7.0, 0),
('L3', 'L2', 3.5, 7.0, 0),
-- L3 <-> L5
('L3', 'L5', 4.0, 9.0, 0),
('L5', 'L3', 4.0, 9.0, 0),
-- L1 <-> L3
('L1', 'L3', 5.0, 12.0, 0),
('L3', 'L1', 5.0, 12.0, 0),
-- L6 <-> L7
('L6', 'L7', 4.5, 11.0, 0),
('L7', 'L6', 4.5, 11.0, 0),
-- L7 <-> L11
('L7', 'L11', 3.0, 7.0, 0),
('L11', 'L7', 3.0, 7.0, 0),
-- L11 <-> L1
('L11', 'L1', 3.8, 9.0, 0),
('L1', 'L11', 3.8, 9.0, 0),
-- L5 <-> L12
('L5', 'L12', 3.2, 6.5, 0),
('L12', 'L5', 3.2, 6.5, 0),
-- L7 <-> L12
('L7', 'L12', 4.0, 8.0, 0),
('L12', 'L7', 4.0, 8.0, 0),
-- L2 <-> L8
('L2', 'L8', 6.5, 15.0, 0),
('L8', 'L2', 6.5, 15.0, 0),
-- L4 <-> L10
('L4', 'L10', 4.2, 9.5, 0),
('L10', 'L4', 4.2, 9.5, 0),
-- L10 <-> L8
('L10', 'L8', 5.0, 11.0, 0),
('L8', 'L10', 5.0, 11.0, 0),
-- L4 <-> L2
('L4', 'L2', 5.5, 14.0, 0),
('L2', 'L4', 5.5, 14.0, 0);

-- Runtime tables (trips, emergencies) start completely empty.
