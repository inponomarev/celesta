node {
    stage ('Clone') {
        checkout scm
    }
    
    stage ('Static analysis'){
        sh 'rm -rf target/idea_inspections'
        docker.image('inponomarev/intellij-idea-analyzer').inside {
           sh '/opt/idea/bin/inspect.sh $WORKSPACE $WORKSPACE/.idea/inspectionProfiles/Project_Default.xml $WORKSPACE/target/idea_inspections -v2'
        }
        sh 'cd target/idea_inspections'
        sh 'sed -i .bak "s,file://\\\$PROJECT_DIR\\\$,${WORKSPACE},g" *.xml'
        
        recordIssues(
           tools: [ideaInspection(pattern: 'target/idea_inspections/*.xml')]
        )
    }
}
