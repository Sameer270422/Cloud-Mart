INSERT INTO products (name, description, category, subcategory, price, stock_quantity)
SELECT 'Wireless Mechanical Keyboard', 'Hot-swappable RGB mechanical keyboard with USB-C', 'Electronics', 'Keyboards', 89.99, 150
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Wireless Mechanical Keyboard');

INSERT INTO products (name, description, category, subcategory, price, stock_quantity)
SELECT '27" 4K Monitor', 'IPS panel, 144Hz, USB-C power delivery', 'Electronics', 'Monitors', 349.00, 60
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = '27" 4K Monitor');

INSERT INTO products (name, description, category, subcategory, price, stock_quantity)
SELECT 'Ergonomic Office Chair', 'Adjustable lumbar support, breathable mesh', 'Furniture', 'Chairs', 219.50, 40
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Ergonomic Office Chair');

INSERT INTO products (name, description, category, subcategory, price, stock_quantity)
SELECT 'Stainless Steel Water Bottle', 'Insulated, 32oz, keeps drinks cold 24h', 'Outdoors', 'Drinkware', 24.99, 300
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Stainless Steel Water Bottle');

INSERT INTO products (name, description, category, subcategory, price, stock_quantity)
SELECT 'Noise Cancelling Headphones', 'Over-ear, 30h battery, Bluetooth 5.3', 'Electronics', 'Audio', 179.99, 90
WHERE NOT EXISTS (SELECT 1 FROM products WHERE name = 'Noise Cancelling Headphones');

-- Backfills subcategory on rows inserted before this column existed (the
-- guards above only cover a completely fresh install - an existing
-- database already has these rows and skips the inserts above entirely).
UPDATE products SET subcategory = 'Keyboards' WHERE name = 'Wireless Mechanical Keyboard' AND subcategory IS NULL;
UPDATE products SET subcategory = 'Monitors' WHERE name = '27" 4K Monitor' AND subcategory IS NULL;
UPDATE products SET subcategory = 'Chairs' WHERE name = 'Ergonomic Office Chair' AND subcategory IS NULL;
UPDATE products SET subcategory = 'Drinkware' WHERE name = 'Stainless Steel Water Bottle' AND subcategory IS NULL;
UPDATE products SET subcategory = 'Audio' WHERE name = 'Noise Cancelling Headphones' AND subcategory IS NULL;
