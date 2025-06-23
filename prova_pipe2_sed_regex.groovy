def fileContent = ''

pipeline {
    //indica su qale nodo verrà eseguita la pipeline
    agent any

    //varie fasi della pipe
    stages {

    stage('se non esiste, crea il file da leggere') {

        steps {
            script {
                if (!fileExists('config.txt')) {
                    writeFile file: 'config.txt', text: 'server_url=http://old-server.com/api username=admin password=12345 log_level=DEBUG'
                }
            }
        }
    }

		
	stage('leggi il file e mostra contenuto') {
		steps {
			script {
				fileContent = readFile 'config.txt'
                echo "Contenuto attuale del file di configurazione: ${fileContent}"
			}
		}
    }

	stage('sostituzione del contenuto del file') {
         steps{
            script{
                //sostituisce vecchio URL col nuovo 
                sh "sed -i 's|server_url=http://old-server.com/api|server_url=http://new-server.com/api|g' ${fileContent}"

                //nasconde (goffamente lmao) la password con una regex
                sh "sed -i 's/password=.*/password=******/g' ${fileContent}"

                //modifica debug solo se è all'inizio della riga (^) il caret punta a ciò nelle RE
                //sh "sed -i 's/^log_level=DEBUG/log_level=INFO/g' config.txt"

                //sostituzione semplice senza il caret (^)
                sh "sed -i 's/log_level=DEBUG/log_level=INFO/g'${fileContent}"


                echo "Il file è stato aggiornato con successo: ${fileContent} "
                
            }
         }
    }
}

    post{
        always {
            echo "Pipeline eseguita."
        }
    }

}