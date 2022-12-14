import groovy.json.JsonBuilder;
import org.common.SonarQubeDetails

void call(String mavenHome, String targetFile,String releaseVersion){
    def sonarKey, sonarProps, sonarResult, sonarProjectName,sonarVersion
    def scannerHome = "/opt/sonar-scanner/bin"

    sonarVersion = releaseVersion
    def sonarExtURL = "http://192.168.0.106:9000"

    node("test"){
        stage("Sonar: Checkout"){
            checkout scm
        }
        stage("Sonar: Validate"){
            sh "pwd"
            sh "ls -ltr"
            sh "whoami"
        }

        //attempt to create the sonar project advance and assign qualitygate
        stage("Sonar: Set-up"){
            def sonarQubeDetails = new SonarQubeDetails()
            Boolean doSetup = true

            try{
                withCredentials([string(credentialsId: 'sonarqube', variable: 'sonarCred')]) {
                    println "SONAR CREDENTIALS ARE READY"
                }
            }
            catch(e){
                println "SONAR CREDENTIALS ARE NOT SET"
                println e
                doSetup = false
            }
            
            if(doSetup){
                withCredentials([string(credentialsId: 'sonarqube', variable: 'sonarCred')]) {
                    def node = readJSON file: targetFile
                    def artifactId = node.version

                    sonarKey = node.name+":"+ "main"
                    sonarProjectName = node.name + " "+ "main"
                    def sonarName = node.name

                    def sonarQualityGateId = sonarQubeDetails.getProjectGate(artifactId)
                    //def defaultQualityGateId = sonarQubeDetails.getProjectGate("default")
                    def url
                    Boolean newProject = false

            
                    //Check is project exists
                    try{
                        url = new URL (sonarExtURL + "/api/projects/search?projects=${sonarKey}")
                        sh "curl -u ${sonarCred}: ${url} -o liveProjects.json"
                        sh "cat liveProjects.json"

                        def liveProjectsJson = readJSON file: "liveProjects.json"

                        //Does the response from sonarqube contain a project
                        if (liveProjectsJson.paging.total == 0){
                            //The project doesn't exist....Create new one
                            try{
                                url = new URL (sonarExtURL + "/api/projects/create")
                                sh "curl -u ${sonarCred}: -d \"project=${sonarKey}&name=${sonarProjectName}\" ${url}"
                                newProject = true
                            }
                            catch( e){
                                println "Was unable to setup sonarProject it may already exist"
                                println e
                            }
                        }
                    }
                    catch(e){
                        println "Something went wrong while checking the sonarProject"
                        println e
                    }


                    if(!newProject){
                        println "The sonar project already exist"
                    }
                    else{
                        println "Assigning the qualityGate " + sonarQualityGateId + " to the sonar Project"
                        try{
                            url = new URL (sonarExtURL + "/api/qualitygates/select")
                            sh "curl -u ${sonarCred}: -d \"projectKey=${sonarKey}&gateId=${sonarQualityGateId}\" ${url}"
                        }
                        catch(e){
                            println "Was unable to assign the quality gate to the project"
                            println e
                        }
                    }
                }
            }
        }

        try{
            stage("Sonar: Analysis"){
                withSonarQubeEnv('sonarqube'){                    
                    sh """  
                        ${scannerHome}/sonar-scanner \
                        -D sonar.projectKey=${sonarKey} \
                        -D sonar.projectName=\"${sonarProjectName}\" \
                        -D sonar.projectVersion=${sonarVersion} \
                        -D sonar.sources=. \
                        -D sonar.exclusions=*/node-modules/** \
                        
                    """
                }
            }

            stage("Sonar: Results"){
                /*File file = new File("/$targetPom");
                def currentPath = new File(file.getParent()).getName().toString();
                println currentPath*/

                //Get the report task written by sonar with taskID
                def props = readProperties file: '.scannerwork/report-task.txt'
                sh "cat .scannerwork/report-task.txt"
                def sonarServerUrl = props['serverUrl']
                def ceTaskUrl = props['ceTaskUrl']
                def ceTask

                withCredentials([string(credentialsId: 'sonarqube', variable: 'sonarCred')]) {
                    //Get analysisId from sonar
                    def url = new URL(ceTaskUrl)
                    echo "waiting for analysis to cpmplete...."
                    def analysisId
                    def attemptCounter = 0

                    while(analysisId == null && attemptCounter < 30){
                        sleep 5
                        sh "curl -u ${sonarCred}: ${url} -o ceTask.json"
                        def ceProps = readJSON file: "ceTask.json"
                        sh "cat ceTask.json"
                        analysisId = ceProps['task']['analysisId']
                        attemptCounter++
                    }
                    echo "ID: $analysisId"
                

                    //Get analsis result from Sonar
                    url = new URL(sonarServerUrl + "/api/qualitygates/project_status?analysisId=" + analysisId)
                    sh "curl -u ${sonarCred}: ${url} -o qualityGate.json"
                    def qgProps = readJSON file: "qualityGate.json"
                    sh "cat qualityGate.json"
                    def qualitygate = qgProps['projectStatus']['status']

                    if(qualitygate == "OK" || qualitygate == "WARN"){
                        echo "Quality Gate passed..... login to ${sonarExtURL}"
                        println(new JsonBuilder(qgProps).toPrettyString())
                        sonarResult = "passed"
                        sonarProps = qgProps
                    }
                    else{
                        echo "Quality Gate failed..... login to ${sonarExtURL}"
                        println(new JsonBuilder(qgProps).toPrettyString())
                        sonarResult = "failure"
                        sonarProps = qgProps
                    }
                }
            }    
        }
        catch(Exception e){
            echo "Error: ${e}"
            echo "SONAR maven run failed: the quality gate will not of been tested"
            sonarResult = "aborted"
            qgProps = [:];
            sonarProps = qgProps
        }
        finally{
            sonarProps.sonarResult = sonarResult
            return sonarProps
        }
    }
}