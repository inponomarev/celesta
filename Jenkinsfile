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
        sh 'rm -rf target/idea_inspections'
        docker
          .image('inponomarev/intellij-idea-analyzer')
          .run('-v $WORKSPACE:/var/project')
        
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )  
    }
}
