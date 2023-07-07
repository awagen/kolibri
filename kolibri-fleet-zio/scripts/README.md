## File Overview

- ```searchEvalQueryFromFileJobDef.json```: Contains a search evaluation definition postable against the job endpoint,
which will persist all processing information in the respective persistence. 
- ```taskSeqJobDef1.json```: More flexible way to compose task sequences, which includes possibility to request several
endpoints, compare the results (such as jaccard similarity).
- ```taskSeqJobDefWithWrapUp1.json```: similar to taskSeqJobDef1.json, yet containing a wrapUpAction which is executed
on wrap up of the job (sequentially if multiple are defined). Note that the example given references a specific folder
(and thus will fail in your setup if you do not have such a folder in the results subfolder) 
since the logic to pick the result folder of the current job (which contains a timeInMillis suffix) is not yet implemented.
Yet it shall demonstrate how tasks such as aggregations can be defined as wrap up actions.
- ```post*.sh scripts```: simple curl of json definitions on the job endpoint, which will persist all processing 
information in the respective persistence. Note that as of now we still need to place a global job directive
(such as an empty file named "KDIR_PROCESS" for processing on all connected nodes) in the job directory to make jobs
start the execution.