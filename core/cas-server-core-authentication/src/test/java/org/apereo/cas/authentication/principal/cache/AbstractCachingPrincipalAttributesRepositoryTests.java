package org.apereo.cas.authentication.principal.cache;

import org.apereo.cas.authentication.AttributeMergingStrategy;
import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.authentication.principal.DefaultPrincipalFactory;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.authentication.principal.PrincipalAttributesRepository;
import org.apereo.cas.authentication.principal.PrincipalFactory;

import lombok.SneakyThrows;
import lombok.val;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import org.apereo.services.persondir.IPersonAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Parent class for test cases around {@link PrincipalAttributesRepository}.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
public abstract class AbstractCachingPrincipalAttributesRepositoryTests {
    private static final String MAIL = "mail";

    protected IPersonAttributeDao dao;

    private final PrincipalFactory principalFactory = new DefaultPrincipalFactory();

    private Map<String, List<Object>> attributes;

    private Principal principal;

    @BeforeEach
    public void initialize() {
        attributes = new HashMap<>();
        attributes.put("a1", Arrays.asList("v1", "v2", "v3"));

        var email = new ArrayList<>();
        email.add("final@example.com");
        attributes.put(MAIL, email);

        attributes.put("a6", Arrays.asList("v16", "v26", "v63"));
        attributes.put("a2", Collections.singletonList("v4"));
        attributes.put("username", Collections.singletonList("uid"));

        this.dao = mock(IPersonAttributeDao.class);
        val person = mock(IPersonAttributes.class);
        when(person.getName()).thenReturn("uid");
        when(person.getAttributes()).thenReturn(attributes);
        when(dao.getPerson(any(String.class), any(IPersonAttributeDaoFilter.class))).thenReturn(person);
        when(dao.getId()).thenReturn(new String[]{"Stub"});

        email = new ArrayList<>();
        email.add("final@school.com");
        this.principal = this.principalFactory.createPrincipal("uid",
            Collections.singletonMap(MAIL, email));
    }

    protected abstract AbstractPrincipalAttributesRepository getPrincipalAttributesRepository(String unit, long duration);

    @Test
    @SneakyThrows
    public void checkExpiredCachedAttributes() {
        val svc = CoreAuthenticationTestUtils.getRegisteredService();
        assertEquals(1, this.principal.getAttributes().size());
        try (val repository = getPrincipalAttributesRepository(TimeUnit.MILLISECONDS.name(), 100)) {
            var repoAttrs = repository.getAttributes(this.principal, svc);
            assertEquals(1, repoAttrs.size());
            assertTrue(repoAttrs.containsKey(MAIL));
            Thread.sleep(500);
            repository.setMergingStrategy(AttributeMergingStrategy.REPLACE);
            repository.setAttributeRepositoryIds(Arrays.stream(this.dao.getId()).collect(Collectors.toSet()));
            repoAttrs = repository.getAttributes(this.principal, svc);
            assertEquals(5, repoAttrs.size());
            assertTrue(repoAttrs.containsKey("a2"));
            assertEquals("final@example.com", repoAttrs.get(MAIL));
        }
    }

    @Test
    @SneakyThrows
    public void ensureCachedAttributesWithUpdate() {
        val svc = CoreAuthenticationTestUtils.getRegisteredService();
        try (val repository = getPrincipalAttributesRepository(TimeUnit.SECONDS.name(), 5)) {
            assertEquals(1, repository.getAttributes(this.principal, svc).size());
            assertTrue(repository.getAttributes(this.principal, svc).containsKey(MAIL));
            attributes.clear();
            assertTrue(repository.getAttributes(this.principal, svc).containsKey(MAIL));
        }
    }

    @Test
    @SneakyThrows
    public void verifyMergingStrategyWithNoncollidingAttributeAdder() {
        val svc = CoreAuthenticationTestUtils.getRegisteredService();
        try (val repository = getPrincipalAttributesRepository(TimeUnit.SECONDS.name(), 5)) {
            repository.setMergingStrategy(AttributeMergingStrategy.ADD);
            repository.setAttributeRepositoryIds(Collections.singleton("Stub"));
            val repositoryAttributes = repository.getAttributes(this.principal, svc);
            assertTrue(repositoryAttributes.containsKey(MAIL));
            assertEquals("final@school.com", repositoryAttributes.get(MAIL).toString());
        }
    }

    @Test
    @SneakyThrows
    public void verifyMergingStrategyWithReplacingAttributeAdder() {
        val svc = CoreAuthenticationTestUtils.getRegisteredService();
        try (val repository = getPrincipalAttributesRepository(TimeUnit.SECONDS.name(), 5)) {
            repository.setAttributeRepositoryIds(Collections.singleton("Stub"));
            repository.setMergingStrategy(AttributeMergingStrategy.REPLACE);
            val repositoryAttributes = repository.getAttributes(this.principal, svc);
            assertTrue(repositoryAttributes.containsKey(MAIL));
            assertEquals("final@example.com", repositoryAttributes.get(MAIL).toString());
        }
    }

    @Test
    @SneakyThrows
    public void verifyMergingStrategyWithMultivaluedAttributeMerger() {
        try (val repository = getPrincipalAttributesRepository(TimeUnit.SECONDS.name(), 5)) {
            repository.setAttributeRepositoryIds(Collections.singleton("Stub"));
            repository.setMergingStrategy(AttributeMergingStrategy.MULTIVALUED);
            val repoAttr = repository.getAttributes(this.principal, CoreAuthenticationTestUtils.getRegisteredService());
            val mailAttr = repoAttr.get(MAIL);
            assertTrue(mailAttr instanceof List);
            val values = (List) mailAttr;
            assertTrue(values.contains("final@example.com"));
            assertTrue(values.contains("final@school.com"));
        }
    }
}
