def fileContent = ''

pipeline {
    //indica su qale nodo verrà eseguita la pipeline
    agent any

    //varie fasi della pipe
    stages {

    stage('se non esiste, crea il file da leggere') {

        steps {
            script {
                if (!fileExists('input.txt')) {
                    writeFile file: 'input.txt', text: 'Contenuto di prova abc123'
                    echo "input.txt creato. "
                }
            }
        }
    }

		
	stage('leggi il file e mostra contenuto') {
		steps {
			script {
				fileContent = readFile 'input.txt'
                echo "Contenuto attuale del file input.txt: ${fileContent}"
			}
		}
    }

	stage('scrittura del file') {
         steps{
            script{
                // def newContent = "Questo è il contenuto del file input.txt: $(fileContent) " check PARENTESI GRAFFE X VARIABILI
                writeFile file: 'output.txt' , text: "il contenuto del file era: ${fileContent}"
                echo "Il file output.txt è stato scritto con successo. "
                
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