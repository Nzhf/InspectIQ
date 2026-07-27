-- V1__init_schema.sql
-- Initial schema creation for InspectIQ

CREATE TABLE production_batches (
    id UUID PRIMARY KEY,
    batch_code VARCHAR(255) UNIQUE NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE defect_types (
    id SERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE inspection_results (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    result VARCHAR(50) NOT NULL,
    inspected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    defect_type_id INTEGER,
    raw_data JSONB,
    CONSTRAINT fk_inspection_batch 
        FOREIGN KEY (batch_id) 
        REFERENCES production_batches (id) 
        ON DELETE RESTRICT,
    CONSTRAINT fk_inspection_defect 
        FOREIGN KEY (defect_type_id) 
        REFERENCES defect_types (id) 
        ON DELETE RESTRICT,
    CONSTRAINT chk_defect_only_on_fail 
        CHECK (result = 'FAIL' OR defect_type_id IS NULL)
);

-- Indexes to support analytical queries and fast lookups
CREATE INDEX idx_inspection_results_batch_id ON inspection_results(batch_id);
CREATE INDEX idx_inspection_results_inspected_at ON inspection_results(inspected_at);
