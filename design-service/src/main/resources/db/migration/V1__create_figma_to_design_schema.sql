CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    figma_key INTEGER NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS pages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    project_id BIGINT NOT NULL,
    CONSTRAINT fk_pages_project
        FOREIGN KEY (project_id)
        REFERENCES projects (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS web_components (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    page_id BIGINT NOT NULL,
    html_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_web_components_page
        FOREIGN KEY (page_id)
        REFERENCES pages (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS figma_components (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    page_id BIGINT NOT NULL,
    CONSTRAINT fk_figma_components_page
        FOREIGN KEY (page_id)
        REFERENCES pages (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id BIGSERIAL PRIMARY KEY,
    date TIMESTAMP NOT NULL,
    comment VARCHAR(4000) NOT NULL,
    page_id BIGINT NOT NULL,
    CONSTRAINT fk_ai_recommendations_page
        FOREIGN KEY (page_id)
        REFERENCES pages (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS similarity_results (
    id BIGSERIAL PRIMARY KEY,
    date TIMESTAMP NOT NULL,
    validation BOOLEAN NOT NULL,
    score REAL NOT NULL,
    result TEXT NOT NULL,
    page_id BIGINT NOT NULL,
    ai_recommendation_id BIGINT NOT NULL,
    CONSTRAINT fk_similarity_results_page
        FOREIGN KEY (page_id)
        REFERENCES pages (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_similarity_results_recommendation
        FOREIGN KEY (ai_recommendation_id)
        REFERENCES ai_recommendations (id)
        ON DELETE CASCADE
);