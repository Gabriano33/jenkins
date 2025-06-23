@Library('pippo-library@master') 

//funzione ricorsiva che recupera tutti i progetti associati a un gruppo, compresi quelli nei sottogruppi.
//Costruisce l'URL per interrogare l'API di GitLab e ottenere i sottogruppi associati a GROUP_ID.
def getListaProject(API_GITLAB_URL, GROUP_ID, listaProjectMap){
    def projectsApiUrl = "${API_GITLAB_URL}/groups/${GROUP_ID}/subgroups"
    echo "Interrogando l'API per il gruppo: ${projectsApiUrl}"

    //recupero dei sottogruppi
    def subgroups=""
    // Effettua una richiesta curl all'API di GitLab usando un token di accesso salvato in credenziali Jenkins.
    // Il risultato è salvato in response e viene convertito in JSON.
    withCredentials([usernamePassword(credentialsId: 'jenkins-api-token-gitlab', passwordVariable: 'PRIVATE_TOKEN', usernameVariable: 'userName')]) {
        def response = sh (
            script: """
                curl --silent --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "${projectsApiUrl}"
            """, 
            returnStdout: true
        )       
        //iterazione sui sottogruppi
        //verifica se subgroups contiene dei dati.
        if (!response) {
            echo "No data received from GitLab API"
            return
        }
        subgroups = readJSON(text: response) 
    }
    //Itera su ogni sottogruppo e ne recupera l'ID (subgroupId).
    //Chiama ricorsivamente getListaProject per ottenere anche i progetti dei sottogruppi.
    if (subgroups instanceof List && subgroups?.size() > 0) { 
        def subgroupsFound = subgroups
        subgroupsFound.each { item ->
            def subgroupId = item.id
            echo "SUBGROUPID: $subgroupId"
            getListaProject(API_GITLAB_URL, subgroupId, listaProjectMap)
        }
    }
    //recupero dei progetti del gruppo (quando non ci sono più sottogruppi)
    //SE IL GRUPPO NON HA SOTTOGRUPPI, ESTRAE I PROGETTI DIRETTAMENTE
    else {
        projectsApiUrl = "${API_GITLAB_URL}/groups/${GROUP_ID}/projects"
        echo "Interrogando l'API per il gruppo: ${projectsApiUrl}"

        def projects=""
        //esegue un'altra curl per ottenere i progetti del gruppo.
        withCredentials([usernamePassword(credentialsId: 'jenkins-api-token-gitlab', passwordVariable: 'PRIVATE_TOKEN', usernameVariable: 'userName')]) {
            def response = sh (
                script: """
                    curl --silent --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "${projectsApiUrl}"
                """, 
                returnStdout: true
            )
            if (!response) {
                echo "No data received from GitLab API"
                return
            }
            projects = readJSON(text: response) 
        }

        //ITERAZIONE SUI PROGETTI E SALVATAGGIO NELLA MAPPA.
        //ITERA SU TUTTI I PROGETTI DEL GRUPPO E SALVA L'ID DEL PROGETTO COME CHIAVE E L'URL SSH COME VALORE
        //alla fine, listaPorjectMap CONTIENE tutti i progetti (con URL SSH) trovati nei gruppi e sottogruppi
        def projectsFound = projects
            projectsFound.each { item -> //ITERA SU OGNI PROGETTO TROVATO
            def projectsId = item.id
            def urlgit = item.ssh_url_to_repo
            listaProjectMap[projectsId] = urlgit //SALVA L'URL SSH NELLA MAPPA
        }
    }
}

// Variabili globali di configurazione
def errorLog = ''
def branchScelto = 'develop'
def groupId = params.groupId // ID del gruppo GitLab (che contiene progetti e sottogruppi)
def emailTo = 'mail.com'
def branchCompile = params.branchCompile ?: 'develop'
def listaProjectMap = [:]
def listaCsproj = [:]
def token = ''


def NEW_VERSION = '1.8.0'
def API_GITLAB_URL = 'https://indirizzo.com/api/v4'
def PROJECT_ID = ''
def PRIVATE_TOKEN = ''
// Variabili per il clone (ad es. URL SSH configurato nella libreria)
def GIT_CREDENTIALS_ID = gitUtils.getUrlbyName("CredentialGit")


pipeline {
    agent any
    // triggers {
    //     // Esegue la pipeline ogni lunedì alle 02:00
    //     cron('0 2 * * 1')
    // }
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
                    //popola listaProjectMap (chiave = projectId, valore = URL SSH)
                    getListaProject(API_GITLAB_URL, groupId, listaProjectMap) 

                    //Itera su listaProjectMap per clonare ogni repository e cercare il file .csproj
                    //Iterazione sui progetti e ricerca dei file .csproj
                    //Itera su listaProjectMap, che contiene gli URL SSH di tutti i repository GitLab trovati.
                    listaProjectMap.each { key, value ->
                        println "$key -> $value"
                            //cleanWs() per evitare file residui tra iterazioni.
                            cleanWs()
                            
                            // Effettua il checkout del repository (usando l'URL SSH in 'value')
                            gitUtils.checkoutLinux(gitUtils.getUrlbyName("CredentialGit"), value, branchCompile)

                            //Esegue find per individuare i file .csproj nel repository e ne estrae il percorso assoluto.
                            def csprojPath = sh(
                                script: "find \"\$WORKSPACE\" -name '*.csproj'", //trova il percorso assoluto
                                returnStdout: true
                            ).trim()
                            
                            // Rimuoviamo la parte di percorso relativa a WORKSPACE per avere un PATH RELATIVO.
                            // Salva il percorso relativo nella mappa listaCsproj.
                            def relativePath = csprojPath.replace("${WORKSPACE}", "")
                            listaCsproj[value] = relativePath
                            
                            // Costruiamo il percorso assoluto del file .csproj.
                            // garantisce che il comando sed abbia il percorso completo del file.
                            def absolutePath = "${WORKSPACE}" + relativePath
                            
                            // Chiamata alla funzione sedFilename (typeA == 2):
                            // typeA: 2 (per modificare la riga <Version>; operazione di sostituzione)
                            // pattern: stringa vuota, poiché non necessaria per typeA==2
                            // replacement: NEW_VERSION
                            // filename: il percorso assoluto del file .csproj
                            labUtils.sedFilename(2, '', NEW_VERSION, absolutePath)
                            
                            // sh "sed -i '/<Version>/c <Version>${replacement}</Version>' " + filename 
                            // Qui si cerca la riga che contiene il tag <Version> e la si sostituisce interamente con la stringa <Version>${replacement}</Version>. 
                            // Quindi, il parametro pattern non viene usato in questa modalità
                            // con typeA == 2, puoi passare una stringa vuota per pattern (ad esempio sedFilename(2, '', NEW_VERSION, filename)) 
                            // perché il comando sed si basa esclusivamente sul tag <Version> e sul valore di replacement.


                    }
                    // printa la lista w/ key e value
                    listaCsproj.each{ k, v -> println "${k}:${v}" }
                }
            }
        }
        //https://www.youtube.com/watch?v=lCSMialN7kU check job trigger su interfaccia jenkins
        stage('Trigger seconda pipe per compilazione') {
            steps{
                build 'testPluginCustomCompile'
            }
        }
        stage('Dopo trigger seconda pipe'){
            steps{
                echo '===DOPO TRIGGER SECONDA PIPE==='
            }
        }
        

        //sharing files between jenk pipelines
        // stage('Esegui operazioni e registra errori') {
        //      steps {
        //          sh "echo \"hello world\" > hello.txt"
        //      archiveArtifacts artifacts: 'hello.txt', fingerprint: true
        //      }
        // }


        // E NELLA PIPELINE 2:
        // pipeline {
        //     agent any

        //     stages {
        //         stage('Hello') {
        //             steps {
        //                 copyArtifacts projectName: 'pipeline1',
        //                     fingerprintArtifacts: true,
        //                     filter: 'hello.txt'
        //             }
        //         }
        //     }
        // }

    } //fine stages
    post {
        always {
            echo "Pipeline eseguita."
            cleanWs()
        }
    }
}
