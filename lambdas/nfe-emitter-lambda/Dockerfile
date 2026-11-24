# Imagem OFICIAL da AWS para Lambda Java 21. Ela ja traz o Runtime Interface Emulator (RIE):
# localmente expoe a mesma API de Invoke da Lambda em :8080/2015-03-31/functions/function/invocations.
# Deploy na AWS: esta mesma imagem vai para o ECR e vira a funcao (PackageType=Image).
FROM public.ecr.aws/lambda/java:21

COPY target/nfe-emitter-lambda-*-lambda.jar ${LAMBDA_TASK_ROOT}/lib/

CMD ["br.com.marketplace.nfe.NfeEmitterHandler::handleRequest"]
