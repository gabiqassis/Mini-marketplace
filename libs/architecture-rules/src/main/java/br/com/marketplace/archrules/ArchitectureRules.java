package br.com.marketplace.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * TRAVAS DE ARQUITETURA (ADR-0009). Cada servico roda estas regras no proprio build:
 * <pre>
 *   @AnalyzeClasses(packages = "br.com.marketplace.order")
 *   class ArchitectureTest { @ArchTest static final ArchTests locks = ArchTests.in(ArchitectureRules.class); }
 * </pre>
 * Camadas: api -> application -> domain <- infra (domain nao conhece ninguem).
 */
public final class ArchitectureRules {

    private ArchitectureRules() {}

    @ArchTest
    public static final ArchRule DOMAIN_IS_PURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infra..", "..api..",
                    "org.springframework..", "org.apache.kafka..", "jakarta..", "javax..", "com.fasterxml..", "io.github.resilience4j..")
            .because("O dominio so conhece Java puro: regras de negocio testaveis sem framework");

    @ArchTest
    public static final ArchRule APPLICATION_DOES_NOT_KNOW_API_OR_INFRA = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infra..")
            .because("Casos de uso dependem de portas (interfaces do dominio), nunca de adaptadores");

    @ArchTest
    public static final ArchRule API_DOES_NOT_KNOW_INFRA = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .because("Controllers falam com a camada application, nunca direto com banco ou Kafka");

    @ArchTest
    public static final ArchRule INFRA_DOES_NOT_KNOW_API = noClasses()
            .that().resideInAPackage("..infra..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    public static final ArchRule SQL_ONLY_IN_INFRA = noClasses()
            .that().resideOutsideOfPackage("..infra..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .because("SQL fica nos repositorios da camada infra");

    @ArchTest
    public static final ArchRule KAFKA_LISTENERS_ONLY_IN_INFRA = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..infra..")
            .should().beAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
            .because("Listener e adaptador de entrada: le o envelope e chama um caso de uso");

    @ArchTest
    public static final ArchRule RABBIT_LISTENERS_ONLY_IN_INFRA = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..infra..")
            .should().beAnnotatedWith("org.springframework.amqp.rabbit.annotation.RabbitListener")
            .because("Consumidor de fila e adaptador de entrada, como o listener Kafka");

    @ArchTest
    public static final ArchRule AWS_SDK_ONLY_IN_INFRA = noClasses()
            .that().resideOutsideOfPackage("..infra..")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")
            .because("SQS e Lambda sao detalhes de infraestrutura: o caso de uso fala com uma porta");

    @ArchTest
    public static final ArchRule NO_FIELD_INJECTION = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Injecao por construtor: dependencias explicitas e testaveis");

    @ArchTest
    public static final ArchRule NO_CYCLES_BETWEEN_LAYERS = slices()
            .matching("br.com.marketplace.*.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    public static final ArchRule NO_STANDARD_STREAMS = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    public static final ArchRule NO_JAVA_UTIL_LOGGING = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
