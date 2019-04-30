@Library('ratcheting') _

node {
    def server = Artifactory.server 'ART'
    def rtMaven = Artifactory.newMavenBuild()
    def buildInfo
    def oldWarnings

    stage ('Clone') {
        checkout scm
    }
    
    try {
      stage ('Static analysis'){
          docker.image('inponomarev/intellij-idea-analyzer').inside {
               sh 'idea/bin/inspect.sh $(pwd) $(pwd)/.idea/inspectionProfiles/Project_Default.xml $(pwd)/target/idea_inspections -v2'
          }
      }
    } finally {
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )
    }
}
