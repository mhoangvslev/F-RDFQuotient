#!/bin/bash

#./summarize.sh bsbm/bsbm100m.nt 2pweak true
#./summarize.sh bsbm/bsbm100m.nt 2pstrong true

# shortcut
java -cp target/quotientSummaryWithDependencies-1.5-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd load /data/datasets/bsbm/bsbm100m_2pw.nt true true
./summarize.sh bsbm/bsbm100m_2pw.nt 2pweak true

java -cp target/quotientSummaryWithDependencies-1.5-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd load /data/datasets/bsbm/bsbm100m_2ps.nt true true
./summarize.sh bsbm/bsbm100m_2ps.nt 2pstrong true
