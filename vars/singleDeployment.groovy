void call(String deployRepoURL, String envcongTag, String repoName,String chartname, String globalenvconfigTag){
    
    node("kube-master"){
        stage('checkout'){
            checkout scm: [$class: 'GitSCM',
                            branches: [[name: "main"]],
                            userRemoteConfigs: [[credentialsId: 'github-cred-with-username', url: deployRepoURL]]
            ]
        }
        stage('Chart Linting'){
            withCredentials([kubeconfigContent(credentialsId: 'KUBE-CONFIG', variable: 'KUBECONFIG_CONTENT')]) {
                    dir("charts/"){
                    sh 'ls -la'
                    sh "helm lint ."       
                    } 

            }
        }
        stage('Deploying application on k8s'){
            withCredentials([kubeconfigContent(credentialsId: 'KUBE-CONFIG', variable: 'KUBECONFIG_CONTENT')]) {
                dir("charts/"){
                    sh "helm upgrade --install --namespace ${envcongTag} ${chartname} . --debug --timeout 900s --wait" 
                }
            }
        }
    }
}