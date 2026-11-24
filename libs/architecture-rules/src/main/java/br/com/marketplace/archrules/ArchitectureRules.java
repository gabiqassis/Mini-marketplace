package br.com.marketplace.archrules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Travas de arquitetura (ADR-0009). Cada servico roda estas regras no proprio build:
 * <pre>
 *   &#64;AnalyzeClasses(packages = "br.com.marketplace.order")
 *   class ArchitectureTest { &#64;ArchTest static final ArchTests locks = ArchTests.in(ArchitectureRules.class); }
 * </pre>
 * Hexagonal: adapter.in -> application.port.in -> application.service -> application.port.out <- adapter.out,
 * com o dominio no centro sem conhecer ninguem.
 */
public final class ArchitectureRules {

    private ArchitectureRules() {}

    @ArchTest
    public static final ArchRule DOMAIN_IS_PURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..adapter..", "..config..",
                    "org.springframework..", "org.apache.kafka..", "jakarta..", "javax..", "com.fasterxml..", "io.github.resilience4j..")
            .because("O dominio so conhece Java puro: regras de negocio testaveis sem framework");

    @ArchTest
    public static final ArchRule APPLICATION_DOES_NOT_KNOW_ADAPTERS = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..config..")
            .because("Casos de uso dependem de portas, nunca de adaptadores");

    @ArchTest
    public static final ArchRule INBOUND_ADAPTERS_ONLY_USE_INBOUND_PORTS = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage("..application.service..", "..application.port.out..", "..adapter.out..")
            .because("Controllers e listeners chamam o caso de uso pela porta de entrada");

    @ArchTest
    public static final ArchRule OUTBOUND_ADAPTERS_DO_NOT_KNOW_INBOUND = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapter.in..", "..application.service..")
            .because("Adaptadores de saida so implementam portas de saida");

    @ArchTest
    public static final ArchRule SQL_ONLY_IN_PERSISTENCE_ADAPTER = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
            .because("SQL fica nos repositorios do adaptador de persistencia");

    @ArchTest
    public static final ArchRule KAFKA_LISTENERS_ONLY_IN_INBOUND_ADAPTERS = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..adapter.in..")
            .should().beAnnotatedWith("org.springframework.kafka.annotation.KafkaListener")
            .because("Listener e adaptador de entrada: le o envelope e chama um caso de uso");

    @ArchTest
    public static final ArchRule RABBIT_LISTENERS_ONLY_IN_INBOUND_ADAPTERS = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..adapter.in..")
            .should().beAnnotatedWith("org.springframework.amqp.rabbit.annotation.RabbitListener")
            .because("Consumidor de fila e adaptador de entrada, como o listener Kafka");

    @ArchTest
    public static final ArchRule AWS_SDK_ONLY_IN_ADAPTERS_AND_CONFIG = noClasses()
            .that().resideOutsideOfPackages("..adapter..", "..config..")
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
