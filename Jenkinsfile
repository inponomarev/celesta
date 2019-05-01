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
        docker.image('inponomarev/intellij-idea-analyzer').inside {
           sh 'mkdir -p ~/${IDEA_CONFIG_DIR}/config/options'
           sh 'ln -s /opt/idea/jdk.table.xml ~/${IDEA_CONFIG_DIR}/config/options/jdk.table.xml'
           sh '/opt/idea/bin/inspect.sh $(pwd) $(pwd)/.idea/inspectionProfiles/Project_Default.xml $(pwd)/target/idea_inspections -v2'
        }
        
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )  
    }
}
