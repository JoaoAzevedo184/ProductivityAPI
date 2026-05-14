-- ============================================================
-- V2: dados iniciais para desenvolvimento
-- Substitui o antigo data.sql do Spring Boot.
-- ============================================================

INSERT INTO tasks (title, description, status, priority, created_at) VALUES
    ('Estudar Spring Boot',    'Revisar JPA e validações',                'IN_PROGRESS', 'HIGH',   CURRENT_TIMESTAMP),
    ('Configurar CI/CD',       'GitHub Actions com deploy em staging',    'PENDING',     'MEDIUM', CURRENT_TIMESTAMP),
    ('Documentar API',         'Atualizar docs/api.md',                   'PENDING',     'LOW',    CURRENT_TIMESTAMP),
    ('Criar Dockerfile',       'Containerizar aplicação Spring Boot',     'IN_PROGRESS', 'HIGH',   CURRENT_TIMESTAMP),
    ('Subir banco PostgreSQL', 'Configurar docker-compose',               'PENDING',     'HIGH',   CURRENT_TIMESTAMP),
    ('Testar endpoints',       'Usar Postman para validar API',           'IN_PROGRESS', 'MEDIUM', CURRENT_TIMESTAMP),
    ('Refatorar código',       'Aplicar clean code e padrões',            'PENDING',     'MEDIUM', CURRENT_TIMESTAMP),
    ('Implementar logs',       'Adicionar logs estruturados',             'COMPLETED',   'LOW',    CURRENT_TIMESTAMP),
    ('Criar README',           'Adicionar instruções do projeto',         'COMPLETED',   'LOW',    CURRENT_TIMESTAMP),
    ('Planejar arquitetura',   'Definir camadas e responsabilidades',     'COMPLETED',   'HIGH',   CURRENT_TIMESTAMP),
    ('Implementar DTOs',       'Separar entrada e saída da API',          'IN_PROGRESS', 'HIGH',   CURRENT_TIMESTAMP),
    ('Criar Exception Handler','Padronizar erros da API',                 'COMPLETED',   'HIGH',   CURRENT_TIMESTAMP),
    ('Configurar Swagger',     'Documentar endpoints automaticamente',    'IN_PROGRESS', 'MEDIUM', CURRENT_TIMESTAMP),
    ('Estudar DevOps',         'Aprender pipelines e containers',         'PENDING',     'HIGH',   CURRENT_TIMESTAMP),
    ('Melhorar performance',   'Analisar queries e otimizar',             'PENDING',     'MEDIUM', CURRENT_TIMESTAMP);