void call(String deployRepoURL, String envcongTag, String repoName, String globalenvconfigTag){
    
    node("worker_docker_slave"){
        stage('checkout'){
            checkout scm: [$class: 'GitSCM',
                            branches: [[name: "master"]],
                            userRemoteConfigs: [[credentialsId: 'BitbucketSSH', url: deployRepoURL]]
            ]
        }
        stage('Chart Linting'){
            withCredentials([kubeconfigContent(credentialsId: 'KUBE-CONFIG', variable: 'KUBECONFIG_CONTENT')]) {
                dir("charts"){
                    sh "helm lint ipt-code/ipt-code-svc"        
                }
            }
        }
        stage('Deploying application on k8s'){
            withCredentials([kubeconfigContent(credentialsId: 'KUBE-CONFIG', variable: 'KUBECONFIG_CONTENT')]) {
                dir("charts"){
                    sh "helm upgrade --install --namespace ${envcongTag} example --debug --timeout 900s --wait" 
                }
            }
        }
    }
}