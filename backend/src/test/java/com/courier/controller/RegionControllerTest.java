package com.courier.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.courier.entity.Region;
import com.courier.repository.RegionRepository;
import com.courier.repository.ShipmentRepository;

@ExtendWith(MockitoExtension.class)
class RegionControllerTest {
    @Mock
    private RegionRepository repository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegionController controller;

    @Test
    void generatesUniqueThreeLetterDistrictCodesFromDistrictNameLetters() {
        Set<String> codes = new HashSet<>(Set.of("CHE"));
        when(repository.existsByCodeIgnoreCase(any()))
                .thenAnswer(invocation -> codes.contains(invocation.getArgument(0, String.class)));
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(repository.save(any(Region.class))).thenAnswer(invocation -> {
            Region saved = invocation.getArgument(0);
            codes.add(saved.getCode());
            return saved;
        });

        Region chennai = controller.create("ADMIN", "ALL", district("Temi-Chennai", "Chennai"));
        Region madurai = controller.create("ADMIN", "ALL", district("Temi-Madurai", "Madurai"));

        assertNotEquals("CHE", chennai.getCode());
        assertNotEquals(chennai.getCode(), madurai.getCode());
        assertEquals(3, madurai.getCode().length());
        assertTrue(madurai.getCode().chars().allMatch(character -> "MADURAI".indexOf(character) >= 0));
        assertEquals("Chennai", chennai.getDistrict());
        assertEquals("Madurai", madurai.getDistrict());
    }

    @Test
    void appendsNumericSuffixOnlyWhenNameLetterCombinationsAreExhausted() {
        Set<String> codes = new HashSet<>(Set.of("AAA"));
        when(repository.existsByCodeIgnoreCase(any()))
                .thenAnswer(invocation -> codes.contains(invocation.getArgument(0, String.class)));
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(repository.save(any(Region.class))).thenAnswer(invocation -> {
            Region saved = invocation.getArgument(0);
            codes.add(saved.getCode());
            return saved;
        });

        Region created = controller.create("ADMIN", "ALL", district("Temi-Aaa", "Aaa"));

        assertEquals("AAA001", created.getCode());
    }

    @Test
    void districtLoginRequiresStateAndCountry() {
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);

        Region missingState = district("Temi-Madurai", "Madurai");
        missingState.setState(" ");
        assertThrows(ResponseStatusException.class,
                () -> controller.create("ADMIN", "ALL", missingState));

        Region missingCountry = district("Temi-Chennai", "Chennai");
        missingCountry.setCountry(null);
        assertThrows(ResponseStatusException.class,
                () -> controller.create("ADMIN", "ALL", missingCountry));
    }

    @Test
    void assignsDistrictAndStateFromParentDistrictWhenCreatingRegionLogin() {
        Region parentDistrict = new Region();
        parentDistrict.setName("Temi-Madurai");
        parentDistrict.setDistrict("Madurai");
        parentDistrict.setState("Tamil Nadu");
        parentDistrict.setAccountType("DISTRICT");
        when(repository.findByNameIgnoreCase("Temi-Madurai"))
                .thenReturn(java.util.Optional.of(parentDistrict));
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(repository.save(any(Region.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Region region = new Region();
        region.setName("Madurai East");
        region.setUsername("maduraieast");
        region.setPassword("password");
        region.setDistrict("Tampered district");
        region.setState("Tampered state");

        Region created = controller.create("REGION_ADMIN", "Temi-Madurai", region);

        assertEquals("Madurai", created.getDistrict());
        assertEquals("Tamil Nadu", created.getState());
        assertEquals("Temi-Madurai", created.getParentDistrict());
    }

    @Test
    void globalAdminCreatesRegionUnderSelectedDistrictAndCopiesItsLocation() {
        Region parentDistrict = new Region();
        parentDistrict.setName("Temi-Madurai");
        parentDistrict.setDistrict("Madurai");
        parentDistrict.setState("Tamil Nadu");
        parentDistrict.setCountry("India");
        parentDistrict.setAccountType("DISTRICT");
        when(repository.findByNameIgnoreCase("Temi-Madurai"))
                .thenReturn(java.util.Optional.of(parentDistrict));
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(repository.save(any(Region.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Region input = new Region();
        input.setName("Madurai East");
        input.setUsername("maduraieast");
        input.setPassword("password");
        input.setAccountType("REGION");
        input.setParentDistrict("Temi-Madurai");
        input.setArea("East zone");
        input.setContact("9876543210");

        Region created = controller.create("ADMIN", "ALL", input);

        assertEquals("REGION", created.getAccountType());
        assertEquals("Temi-Madurai", created.getParentDistrict());
        assertEquals("Madurai", created.getDistrict());
        assertEquals("Tamil Nadu", created.getState());
        assertEquals("India", created.getCountry());
        assertEquals("East zone", created.getArea());
        assertEquals("9876543210", created.getContact());
        assertEquals(null, created.getCode());
    }

    @Test
    void contactIsOptionalButMustContainExactlyTenDigitsWhenProvided() {
        when(repository.findByNameIgnoreCase("Temi-Madurai"))
                .thenReturn(java.util.Optional.of(district("Temi-Madurai", "Madurai")));
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(repository.save(any(Region.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Region input = new Region();
        input.setName("Madurai North");
        input.setUsername("madurainorth");
        input.setPassword("password");
        input.setArea(null);

        Region created = controller.create("REGION_ADMIN", "Temi-Madurai", input);
        assertEquals(null, created.getContact());
        assertEquals(null, created.getArea());

        input.setName("Madurai South");
        input.setUsername("maduraisouth");
        input.setContact("987654321");
        assertThrows(ResponseStatusException.class,
                () -> controller.create("REGION_ADMIN", "Temi-Madurai", input));
    }

    @Test
    void globalAdminCannotCreateRegionWithoutAValidDistrictLogin() {
        when(repository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(repository.existsByUsernameIgnoreCase(any())).thenReturn(false);
        Region input = new Region();
        input.setName("Orphan Region");
        input.setUsername("orphan");
        input.setPassword("password");
        input.setAccountType("REGION");
        input.setParentDistrict("Not a district login");
        when(repository.findByNameIgnoreCase("Not a district login"))
                .thenReturn(java.util.Optional.empty());

        assertThrows(ResponseStatusException.class, () -> controller.create("ADMIN", "ALL", input));
    }

    @Test
    void globalAdminCannotDeleteDistrictLoginWhileRegionsDependOnIt() {
        Region district = new Region();
        district.setName("Temi-Madurai");
        district.setAccountType("DISTRICT");
        when(repository.findById(10L)).thenReturn(java.util.Optional.of(district));
        when(repository.findByParentDistrictIgnoreCaseAndAccountTypeIgnoreCase("Temi-Madurai", "REGION"))
                .thenReturn(java.util.List.of(new Region()));

        assertThrows(ResponseStatusException.class, () -> controller.delete("ADMIN", "ALL", 10L));
        verify(repository, never()).delete(district);
    }

    private Region district(String name, String districtName) {
        Region region = new Region();
        region.setName(name);
        region.setDistrict(districtName);
        region.setState("Tamil Nadu");
        region.setCountry("India");
        region.setUsername(name + "admin");
        region.setPassword("password");
        return region;
    }
}
