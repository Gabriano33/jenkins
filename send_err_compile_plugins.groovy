@Library('paperino-library@master') _

// Variabili globali di configurazione
def errorLog = ''
def branchScelto = 'develop'
def groupId = params.groupId // ID del gruppo GitLab (che contiene progetti e sottogruppi)
def emailTo = 'email.com'

def NEW_VERSION = "2.0.0"
def API_GITLAB_URL = 'https://.com/api/v4'
def PROJECT_ID = "439"

// Variabili per il clone (ad es. URL SSH configurato nella libreria)
def GIT_CREDENTIALS_ID = gitUtils.getUrlbyName("CredentialGit")

pipeline {
    agent any
    triggers {
        // Esegue la pipeline ogni lunedì alle 02:00
        cron('0 2 * * 1')
    }
    stages {
        stage('Clean Workspace') {
            steps {
                script {
                    cleanWs()
                }
            }
        }
        stage('Get Projects from gruppi e sottogruppi') {
            steps {
                script {
                    // Costruisci l'URL per ottenere l'elenco dei progetti, inclusi quelli nei sottogruppi
                    def projectsApiUrl = "${API_GITLAB_URL}/groups/${groupId}/subgroups"
                    echo "Interrogando l'API per il gruppo: ${projectsApiUrl}"

                    // Dichiarazione della variabile response
                    def response = ""
                    // Usa withCredentials per passare in modo sicuro il token
                    withCredentials([usernamePassword(credentialsId: 'jenkins-api-token-gitlab', passwordVariable: 'PRIVATE_TOKEN', usernameVariable: 'userName')]) {
                        response = sh (
                            script: """
                                curl --silent --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "${projectsApiUrl}"
                            """, 
                            returnStdout: true
                        ).trim()
                    }
                    echo "API Response: ${response}"
                    
                    // Usa jq per convertire l'array JSON in righe compatte, uno per ogni progetto
                    def projectsOutput = sh(
                        script: """
                            echo '${response}' | jq -c '.[]'
                        """,
                        returnStdout: true
                    ).trim()
                    
                    // Divide l'output in un array: ogni elemento è una stringa JSON rappresentante un progetto
                    def projectsArray = projectsOutput.split("\n")
                    echo "Trovati ${projectsArray.size()} progetti nel gruppo ${groupId}."
                    
                    // Salva l'array in una variabile d'ambiente per usarlo nello stage successivo
                    env.PROJECTS_JSON_ARRAY = projectsArray.join("\n")
                }
            }
        }
        stage('Process Projects and Update .csproj Files') {
            steps {
                script {
                    // Converte la variabile env.PROJECTS_JSON_ARRAY in un array di stringhe
                    def projectsArray = env.PROJECTS_JSON_ARRAY.split("\n")
                    
                    // Itera su ogni progetto nell'array
                    for (def projectJson in projectsArray) {
                        // Estrae il project id e il path_with_namespace usando jq dalla stringa JSON
                        def projectId = sh(
                            script: "echo '${projectJson}' | jq '.id'",
                            returnStdout: true
                        ).trim()
                        def projectPath = sh(
                            script: "echo '${projectJson}' | jq -r '.path_with_namespace'",
                            returnStdout: true
                        ).trim()
                        echo "Processing project: ${projectPath}"
                        
                        // Definisci una directory di clone unica per ogni progetto
                        def cloneDir = "workspace/${projectId}"
                        sh "rm -rf ${cloneDir}"
                        
                        // Clona il repository del progetto sul branch desiderato
                        sh "git clone --branch ${branchScelto} ${project.http_url_to_repo} ${cloneDir}"
                        
                        // Entra nella directory clonata
                        dir(cloneDir) {
                            // Trova ricorsivamente tutti i file .csproj in questo repository
                            def csprojFiles = sh(
                                script: "find . -type f -name '*.csproj'",
                                returnStdout: true
                            ).trim().split("\n")
                            
                            echo "Trovati ${csprojFiles.size()} file .csproj in ${projectPath}."
                            
                            // Itera su ogni file .csproj trovato e aggiorna i riferimenti dei package
                            for (def file in csprojFiles) {
                                echo "Aggiornamento del file: ${file}"
                                sh """
                                    sed -i 's/<PackageReference Include="o\\.Abstractions" Version="[^"]*"/<PackageReference Include="o.Abstractions" Version="${NEW_VERSION}"/g' "${file}"
                                """
                                sh """
                                    sed -i 's/<PackageReference Include="o\\.Domain" Version="[^"]*"/<PackageReference Include="o.Domain" Version="${NEW_VERSION}"/g' "${file}"
                                """
                                sh """
                                    sed -i 's/<PackageReference Include="o\\.CoreDomain" Version="[^"]*"/<PackageReference Include="o.CoreDomain" Version="${NEW_VERSION}"/g' "${file}"
                                """
                            }
                            
                            // (Opzionale) Committa e push delle modifiche
                            // sh "git add ."
                            // sh "git commit -m 'Aggiornata versione a ${NEW_VERSION}' || echo 'Nessuna modifica da committare'"
                            // withCredentials([sshUserPrivateKey(credentialsId: GIT_CREDENTIALS_ID, keyFileVariable: 'SSH_KEY')]) {\n                            //    sh "git push origin ${branchScelto}"\n                            // }
                        }
                    }
                }
            }
        }
        // stage('Compila Progetti') {
        //     steps {
        //         script {
        //             echo "Qui aggiungo uno stage per compilare i progetti aggiornati."
        //         }
        //     }
        // }
    }
    post {
        always {
            echo "Pipeline eseguita."
            cleanWs()
        }
    }
}
