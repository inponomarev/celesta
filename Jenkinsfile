@Library('ratcheting') _

node {
    def server = Artifactory.server 'ART'
    def rtMaven = Artifactory.newMavenBuild()
    def buildInfo
    def oldWarnings

    stage ('Clone') {
        checkout scm
    }
    
    stage ('Static analysis'){
        sh 'docker run --rm -v `pwd`:/var/project inponomarev/intellij-idea-analyzer'
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )  
    }
}
