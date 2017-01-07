package com.mesosphere.sdk.specification;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.FileReader;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.mesosphere.sdk.specification.util.RLimit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

public class DefaultServiceSpecTest {
    @Rule public final EnvironmentVariables environmentVariables = OfferRequirementTestUtils.getApiPortEnvironment();
    @Mock private FileReader mockFileReader;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public void validExhaustive() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file), mockFileReader);
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validMinimal() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validSimple() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-simple.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
    }

    @Test
    public void validPortResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-multiple-ports.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file));

        List<ResourceSpecification> portsResources = serviceSpec.getPods().get(0).getTasks().get(0).getResourceSet()
                .getResources()
                .stream()
                .filter(r -> r.getName().equals("ports"))
                .collect(Collectors.toList());

        Assert.assertEquals(2, portsResources.size());

        Protos.Value.Ranges ports = portsResources.get(0).getValue().getRanges();
        Assert.assertEquals(1, ports.getRangeCount());
        Assert.assertEquals(8080, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());

        ports = portsResources.get(1).getValue().getRanges();
        Assert.assertEquals(8088, ports.getRange(0).getBegin(), ports.getRange(0).getEnd());
    }

    @Test
    public void invalidReplacementFailurePolicy() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-replacement-failure-policy.yml").getFile());
        try {
            YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidPodName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-pod-name.yml").getFile());
        try {
            YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test(expected = FileNotFoundException.class)
    public void invalidConfigFile() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-config-file.yml").getFile());
        YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
    }

    @Test
    public void invalidPodNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file), mockFileReader);
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            pods.add(pods.get(0));
            DefaultServiceSpec.newBuilder(defaultServiceSpec)
                    .pods(pods)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-name.yml").getFile());
        try {
            YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }

    @Test(expected = RLimit.InvalidRLimitException.class)
    public void invalidRLimitName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-rlimit-name.yml").getFile());
        YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
    }

    @Test
    public void invalidTaskNamePojo() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        when(mockFileReader.read("config-three.conf.mustache")).thenReturn("hi");

        DefaultServiceSpec defaultServiceSpec = YAMLServiceSpecFactory.generateServiceSpec(
                YAMLServiceSpecFactory.generateRawSpecFromYAML(file), mockFileReader);
        try {
            List<PodSpec> pods = defaultServiceSpec.getPods();
            PodSpec aPod = pods.get(0);
            List<TaskSpec> tasks = aPod.getTasks();
            tasks.add(tasks.get(0));
            DefaultPodSpec.newBuilder(aPod)
                    .tasks(tasks)
                    .build();
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidTaskSpecNoResource() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-task-resources.yml").getFile());
        try {
            YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (ConstraintViolationException e) {
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            Assert.assertTrue(constraintViolations.size() > 0);
        }
    }

    @Test
    public void invalidResourceSetName() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("invalid-resource-set-name.yml").getFile());
        try {
            YAMLServiceSpecFactory.generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                ConstraintViolationException cause = (ConstraintViolationException) e.getCause();
                Set<ConstraintViolation<?>> constraintViolations = cause.getConstraintViolations();
                Assert.assertTrue(constraintViolations.size() > 0);
            }
        }
    }
}
