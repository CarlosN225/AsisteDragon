pipeline {
    agent any

    stages {
        stage('1. Obtener Codigo de GitHub') {
            steps {
                echo 'Descarga de codigo fuente completada desde GitHub.'
                sh 'ls -la'
            }
        }

        stage('2. Ejecutar Pruebas del Backend') {
            steps {
                sh 'docker run --rm -u $(id -u):$(id -g) -e HOME=/tmp -v "$PWD/backend:/app" -w /app node:20-alpine sh -c "npm ci && npm test"'
                sh 'docker run --rm -u $(id -u):$(id -g) -e HOME=/tmp -v "$PWD/backend-usuarios:/app" -w /app node:20-alpine sh -c "npm ci && node -c src/app.js"'
            }
        }

        stage('3. Construir Imagenes Docker') {
            steps {
                sh 'docker build -t control-visitas-api:jenkins ./backend'
                sh 'docker build -t asistedragon-usuarios:jenkins ./backend-usuarios'
                sh 'docker images | grep -E "control-visitas-api|asistedragon-usuarios"'
            }
        }

        stage('4. Ejecutar y Verificar Contenedor') {
            steps {
                sh '''
                    docker rm -f jenkins-test-api || true
                    docker run -d --name jenkins-test-api -p 3005:3000 control-visitas-api:jenkins
                    sleep 3
                    curl -f http://localhost:3005/health
                    echo ""
                    docker ps | grep jenkins-test-api
                '''
            }
        }
    }

    post {
        always {
            sh 'docker rm -f jenkins-test-api || true'
        }
        success {
            echo 'Pipeline DevOps completado con exito.'
        }
        failure {
            echo 'Fallo una etapa del Pipeline. Revisa el Console Output.'
        }
    }
}
