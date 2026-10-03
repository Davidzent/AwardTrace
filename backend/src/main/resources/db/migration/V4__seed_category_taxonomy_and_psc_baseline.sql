-- The thirteen categories an award can carry (doc 09). The definition is the exact text the classifier prompt uses,
-- so changing one is a new prompt version.
CREATE TABLE taxonomy_category (
    code        text PRIMARY KEY,
    label       text NOT NULL,
    definition  text NOT NULL,
    sort_order  smallint NOT NULL UNIQUE
);

INSERT INTO taxonomy_category (code, label, definition, sort_order) VALUES
    ('IT_SOFTWARE', 'IT software',
     'Software development, modernization, licenses, and software as a service', 1),
    ('IT_INFRASTRUCTURE', 'IT infrastructure',
     'Computers, networks, data centers, cloud hosting, and telecommunications', 2),
    ('CYBERSECURITY', 'Cybersecurity',
     'Security operations, assessments, identity management, and information assurance', 3),
    ('PROFESSIONAL_SERVICES', 'Professional services',
     'Management consulting, program support, and administrative and financial services', 4),
    ('ENGINEERING_RESEARCH', 'Engineering and research',
     'Research and development, engineering, and scientific and technical studies', 5),
    ('CONSTRUCTION_FACILITIES', 'Construction and facilities',
     'Construction, renovation, repair, and operation of buildings and real property', 6),
    ('HEALTH_MEDICAL', 'Health and medical',
     'Medical services, pharmaceuticals, medical equipment, and health programs', 7),
    ('DEFENSE_SYSTEMS', 'Defense systems',
     'Weapons, ammunition, military vehicles, aircraft, ships, and their parts', 8),
    ('LOGISTICS_TRANSPORT', 'Logistics and transport',
     'Freight, shipping, fuel, travel, and vehicle fleets not built for combat', 9),
    ('SUPPLIES_EQUIPMENT', 'Supplies and equipment',
     'General supplies, furniture, tools, and equipment not covered elsewhere', 10),
    ('TRAINING_EDUCATION', 'Training and education',
     'Training delivery, curriculum, and education services', 11),
    ('OTHER', 'Other',
     'A clear description that fits none of the categories above', 12),
    ('UNCLASSIFIABLE', 'Not enough information',
     'The description is too vague to classify, such as "SEE SCHEDULE" or a bare modification number', 13);

-- The free baseline: an award's category from its PSC, by the longest matching prefix, so a specific code overrides
-- its group. PSCs that start with a letter are services; those that start with a digit are products, grouped by
-- their first two digits. Drafted from the official PSC group names; every PSC in scope matches at least its first
-- character.
CREATE TABLE psc_baseline_map (
    psc_prefix  text PRIMARY KEY CHECK (char_length(psc_prefix) BETWEEN 1 AND 4),
    category    text NOT NULL REFERENCES taxonomy_category (code)
);

INSERT INTO psc_baseline_map (psc_prefix, category) VALUES
    ('1', 'DEFENSE_SYSTEMS'),          -- Weapons, ammunition, aircraft, space vehicles, and ships (FSC groups 10 to 19)
    ('2', 'SUPPLIES_EQUIPMENT'),       -- Vehicular, ship, and engine equipment
    ('22', 'LOGISTICS_TRANSPORT'),     -- Railway equipment
    ('23', 'LOGISTICS_TRANSPORT'),     -- Motor vehicles, trailers, and cycles
    ('2350', 'DEFENSE_SYSTEMS'),       -- Combat, assault, and tactical vehicles
    ('3', 'SUPPLIES_EQUIPMENT'),       -- Machinery and tools
    ('4', 'SUPPLIES_EQUIPMENT'),       -- Machinery and equipment
    ('5', 'SUPPLIES_EQUIPMENT'),       -- Hardware, construction materials, and components
    ('58', 'IT_INFRASTRUCTURE'),       -- Communication, detection, and coherent radiation equipment
    ('6', 'SUPPLIES_EQUIPMENT'),       -- Electrical, instrument, and chemical products
    ('65', 'HEALTH_MEDICAL'),          -- Medical, dental, and veterinary equipment and supplies
    ('69', 'TRAINING_EDUCATION'),      -- Training aids and devices
    ('7', 'SUPPLIES_EQUIPMENT'),       -- Furniture, office supplies, and printed matter
    ('70', 'IT_INFRASTRUCTURE'),       -- Information technology equipment (legacy codes)
    ('7030', 'IT_SOFTWARE'),           -- Information technology software (legacy code)
    ('7A', 'IT_SOFTWARE'),             -- IT and telecom: application software
    ('7B', 'IT_INFRASTRUCTURE'),       -- IT and telecom: compute
    ('7C', 'IT_INFRASTRUCTURE'),       -- IT and telecom: data center
    ('7D', 'IT_INFRASTRUCTURE'),       -- IT and telecom: service delivery
    ('7E', 'IT_INFRASTRUCTURE'),       -- IT and telecom: end user
    ('7F', 'IT_INFRASTRUCTURE'),       -- IT and telecom: IT management
    ('7G', 'IT_INFRASTRUCTURE'),       -- IT and telecom: network
    ('7H', 'IT_INFRASTRUCTURE'),       -- IT and telecom: platform
    ('7J', 'CYBERSECURITY'),           -- IT and telecom: security and compliance products
    ('7K', 'IT_INFRASTRUCTURE'),       -- IT and telecom: storage
    ('8', 'SUPPLIES_EQUIPMENT'),       -- Containers, textiles, clothing, food, and agricultural supplies
    ('9', 'SUPPLIES_EQUIPMENT'),       -- Materials and miscellaneous
    ('91', 'LOGISTICS_TRANSPORT'),     -- Fuels, lubricants, oils, and waxes
    ('A', 'ENGINEERING_RESEARCH'),     -- Research and development
    ('B', 'ENGINEERING_RESEARCH'),     -- Special studies and analyses
    ('C', 'ENGINEERING_RESEARCH'),     -- Architect and engineering services
    ('D', 'IT_INFRASTRUCTURE'),        -- IT and telecom services
    ('D302', 'IT_SOFTWARE'),           -- IT and telecom: systems development (legacy code)
    ('D308', 'IT_SOFTWARE'),           -- IT and telecom: programming (legacy code)
    ('D310', 'CYBERSECURITY'),         -- IT and telecom: cyber security and data backup (legacy code)
    ('D317', 'IT_SOFTWARE'),           -- IT and telecom: web-based subscription (legacy code)
    ('D319', 'IT_SOFTWARE'),           -- IT and telecom: annual software maintenance (legacy code)
    ('DA', 'IT_SOFTWARE'),             -- IT and telecom: business application and application development
    ('DJ', 'CYBERSECURITY'),           -- IT and telecom: security and compliance
    ('E', 'CONSTRUCTION_FACILITIES'),  -- Purchase of structures and facilities
    ('F', 'OTHER'),                    -- Natural resources and conservation
    ('G', 'OTHER'),                    -- Social services
    ('H', 'ENGINEERING_RESEARCH'),     -- Quality control, testing, and inspection
    ('J', 'SUPPLIES_EQUIPMENT'),       -- Maintenance, repair, and rebuilding of equipment
    ('K', 'SUPPLIES_EQUIPMENT'),       -- Modification of equipment
    ('L', 'PROFESSIONAL_SERVICES'),    -- Technical representative services
    ('M', 'CONSTRUCTION_FACILITIES'),  -- Operation of government-owned facilities
    ('N', 'SUPPLIES_EQUIPMENT'),       -- Installation of equipment
    ('P', 'CONSTRUCTION_FACILITIES'),  -- Salvage and demolition
    ('P1', 'OTHER'),                   -- Preparation and disposal of excess and surplus property
    ('Q', 'HEALTH_MEDICAL'),           -- Medical services
    ('R', 'PROFESSIONAL_SERVICES'),    -- Professional, administrative, and management support
    ('R425', 'ENGINEERING_RESEARCH'),  -- Professional support: engineering and technical
    ('S', 'CONSTRUCTION_FACILITIES'),  -- Utilities and housekeeping
    ('T', 'PROFESSIONAL_SERVICES'),    -- Photographic, mapping, printing, and publication
    ('U', 'TRAINING_EDUCATION'),       -- Education and training
    ('V', 'LOGISTICS_TRANSPORT'),      -- Transportation, travel, and relocation
    ('W', 'SUPPLIES_EQUIPMENT'),       -- Lease or rental of equipment
    ('X', 'CONSTRUCTION_FACILITIES'),  -- Lease or rental of facilities
    ('Y', 'CONSTRUCTION_FACILITIES'),  -- Construction of structures and facilities
    ('Z', 'CONSTRUCTION_FACILITIES');  -- Maintenance, repair, and alteration of real property
