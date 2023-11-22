pipeline {
    agent any
    triggers {
        githubPush()
    }
    parameters {
        choice(name: 'ENV_VAR', choices: 'choose\ndev\nprod', description: 'Select Environment')
        // string(name: 'ENVIRONMENT', defaultValue: 'dev', description: 'ENVIRONMENT')
    }
    environment {
        ENV_VAR = "${params.ENV_VAR}"
        major_version = '0'
        intermediate_version = '0'
        minor_version = '${BUILD_NUMBER}'
        version_type = 'SNAPSHOT'
        prodPublishPort = "9292"
        devPublishPort = "8282"
    }
    tools {
        maven 'Maven'
    }
    stages {
        stage('Fetching Environment') {
            steps {
                script { 
                    if (env.ENV_VAR == 'choose') {
                        env.ENVIRONMENT = input(
                            id: 'ENVIRONMENT',
                            message: 'Choose ENVIRONMENT:',
                            parameters: [
                                choice(name: 'ENVIRONMENT', choices: 'dev\nprod', description: 'Select Environment')
                            ]
                        )
                    } else {
                        env.ENVIRONMENT = "${ENV_VAR}"
                    }  
                    echo "ENVIRONMENT: ${env.ENVIRONMENT}"
                }
            }
        }
        stage('GetCode') {
            steps {
                checkout scmGit(branches: [[name: "*/${env.ENVIRONMENT}"]], extensions: [], userRemoteConfigs: [[credentialsId: 'gsanjana2712-github', url: 'https://github.com/gsanjana2712/testapp.git']])
            }
        }
        stage('Build') {
            steps {
                sh 'mvn clean install -f pom.xml -Dproject.major_version=${major_version} -Dproject.intermediate_version=${intermediate_version} -Dproject.minor_version=${minor_version} -Dproject.version_type=${version_type}'
            }

        }
        stage('Test') {
            steps {
                sh 'mvn test -Dproject.major_version=${major_version} -Dproject.intermediate_version=${intermediate_version} -Dproject.minor_version=${minor_version} -Dproject.version_type=${version_type}'

            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'pom.xml'
                }
            }
        }
        stage('Junit Test Results') {
            steps {
                junit '*/**/*.xml'
            }
        }

        stage('SonarQube analysis') {

            steps {
                withSonarQubeEnv('Sonarqube') {
                    sh "mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar -Dproject.major_version=${major_version} -Dproject.intermediate_version=${intermediate_version} -Dproject.minor_version=${minor_version} -Dproject.version_type=${version_type}"
                }
            }
        }
        stage('Server') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'sanjanagupta-jfrog', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    rtServer(
                        id: "Artifactory",
                        url: 'http://localhost:8082/artifactory',
                        username: "${USERNAME}",
                        password: "${PASSWORD}",
                        bypassProxy: true,
                        timeout: 300
                    )
                }
            }
        }
        stage('Upload') {
            steps {
                rtUpload(
                    serverId: "Artifactory",
                    spec: '''{
                    "files": [{
                        "pattern": "*.war",
                        "target": "'''+"testapp-${env.ENVIRONMENT}"+'''"
                    }]
                }
                ''',
            )
        }
    }
    stage('Publish build info') {
        steps {
            rtPublishBuildInfo(
                serverId: "Artifactory"
            )
        }
    }
    stage('Deploy Artifact') {
        steps{
            script {
                if (env.ENVIRONMENT == 'dev') {
                    env.publishPort = "${devPublishPort}"
                } else {
                    env.publishPort = "${prodPublishPort}"
                }
                withCredentials([usernamePassword(credentialsId: 'sanjanagupta-jfrog', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh """
                        if curl -s http://localhost:${publishPort}/testapp/ | grep "${ENVIRONMENT}"; then
                            echo "Application is accessible"
                            sudo sh /root/mini-assignment-2/${ENVIRONMENT}/apache-tomcat-9.0.73/bin/shutdown.sh 2>/dev/null || true
                            sudo rm -rf /root/mini-assignment-2/${ENVIRONMENT}/apache-tomcat-9.0.73/webapps/testapp.war
                        else
                            echo "Application is not accessible"
                        fi
                        sudo curl -u ${USERNAME}:${PASSWORD} -L -o /root/mini-assignment-2/${ENVIRONMENT}/apache-tomcat-9.0.73/webapps/testapp.war http://localhost:8082/artifactory/testapp-${ENVIRONMENT}/testapp-${major_version}.${intermediate_version}.${minor_version}-${version_type}.war
                        sudo firewall-cmd --zone=public --add-port=${publishPort}/tcp --permanent
                        sudo firewall-cmd --reload
                        sudo sh /root/mini-assignment-2/${ENVIRONMENT}/apache-tomcat-9.0.73/bin/startup.sh
                    """
                }
            }
        }
      }
    }
    post {
        always {
            script {
                def jobName = currentBuild.fullProjectName
                def buildNumber = currentBuild.number
                
                emailext (
                    body: "Build #${buildNumber} of ${jobName} completed. Check the attached logs for details.",
                    to: 'sanjana.gupta@nagarro.com',
                    subject: "Build Completed - ${jobName} - #${buildNumber}",
                    attachLog: true
                )
            }
        }
    }
}