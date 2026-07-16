-- Add all 22 service categories to match the frontend
-- This migration inserts the new categories that were missing from V12

INSERT INTO categories (name, description, sort_order, is_active) VALUES
    -- Home & Maintenance
    ('Plumbing', 'Plumbing services including repairs and installations', 1, true),
    ('Electrical', 'Electrical services including wiring and repairs', 2, true),
    ('Carpenter', 'Carpentry and woodwork services', 3, true),
    ('Mason', 'Masonry and brickwork services', 4, true),
    ('Painting', 'Interior and exterior painting services', 5, true),

    -- Cleaning & Hygiene
    ('Mama Fua', 'Home cleaning and laundry services', 6, true),
    ('Commercial Cleaning', 'Office and commercial space cleaning services', 7, true),
    ('Carpet & Sofa Cleaning', 'Professional carpet and sofa cleaning services', 8, true),
    ('Pressure Washing', 'Pressure washing for driveways, patios, and exteriors', 9, true),

    -- Outdoor & Garden
    ('Lawn & Compound Maintenance', 'Lawn mowing and compound upkeep services', 10, true),
    ('Hedge & Fence Trimming', 'Hedge trimming and fence maintenance services', 11, true),
    ('Tree Services', 'Tree trimming, removal, and care services', 12, true),
    ('Irrigation & Borehole Services', 'Irrigation system and borehole services', 13, true),
    ('Gardening', 'Gardening and landscaping services', 14, true),

    -- General Services
    ('Cleaning', 'Home and office cleaning services', 15, true),
    ('Appliance Repair', 'Repair of household appliances', 16, true),
    ('Moving', 'Moving and relocation services', 17, true),
    ('Handyman', 'General handyman and repair services', 18, true),
    ('Tutoring', 'Academic tutoring and lessons', 19, true),
    ('Pet Care', 'Pet grooming, walking, and care services', 20, true),
    ('Health', 'Health and wellness services', 21, true)
ON CONFLICT (name) DO NOTHING;