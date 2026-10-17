-- Agrega la gestión de categorías sin afectar las tareas ya existentes.

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    color VARCHAR(20) NOT NULL,
    CONSTRAINT chk_category_color CHECK (color IN ('RED', 'BLUE', 'GREEN', 'YELLOW')),
    CONSTRAINT uq_category_name UNIQUE (name)
);

ALTER TABLE tasks
    ADD COLUMN category_id BIGINT NULL,
    ADD CONSTRAINT fk_task_category FOREIGN KEY (category_id) REFERENCES categories(id);
