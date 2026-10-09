package com.courier.controller;

import com.courier.entity.Region;
import com.courier.repository.RegionRepository;
import com.courier.repository.ShipmentRepository;
import com.courier.entity.Shipment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/regions")
public class RegionController {
    private final RegionRepository repository;
    private final ShipmentRepository shipmentRepository;
    private final PasswordEncoder passwordEncoder;

    public RegionController(RegionRepository repository, ShipmentRepository shipmentRepository,
                            PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.shipmentRepository = shipmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Region> all(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role,
                            @RequestHeader(value = "X-Operating-Region", defaultValue = "") String operatingRegion) {
        if ("ADMIN".equalsIgnoreCase(role)) return repository.findAll();
        if ("REGION_ADMIN".equalsIgnoreCase(role) && operatingRegion != null && !operatingRegion.isBlank()) {
            return repository.findByParentDistrictIgnoreCase(operatingRegion.trim());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Region list access denied");
    }

    @PostMapping
    public Region create(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role,
                         @RequestHeader(value = "X-Operating-Region", defaultValue = "") String operatingRegion,
                         @RequestBody Region region) {
        boolean global = "ADMIN".equalsIgnoreCase(role);
        boolean district = "REGION_ADMIN".equalsIgnoreCase(role)
                && operatingRegion != null && !operatingRegion.isBlank()
                && !"ALL".equalsIgnoreCase(operatingRegion);
        boolean creatingRegion = global && "REGION".equalsIgnoreCase(region.getAccountType());
        if (!global && !district) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can create region logins");
        }
        if (region.getName() == null || region.getName().isBlank()
                || region.getUsername() == null || region.getUsername().isBlank()
                || region.getPassword() == null || region.getPassword().isBlank()
                || repository.existsByNameIgnoreCase(region.getName().trim())
                || repository.existsByUsernameIgnoreCase(region.getUsername().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region name and unique login credentials are required");
        }
        if (global && !creatingRegion
                && (region.getState() == null || region.getState().isBlank()
                    || region.getCountry() == null || region.getCountry().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "State and country are required for district logins");
        }
        normalizeContact(region);
        region.setName(region.getName().trim());
        if (global && !creatingRegion) {
            region.setDistrict(districtNameFromLoginName(region.getName()));
        }
        region.setUsername(region.getUsername().trim());
        String password = region.getPassword();
        region.setPasswordHash(passwordEncoder.encode(password));
        region.setPassword(null);
        if (global && !creatingRegion) {
            region.setCode(uniqueDistrictCode(region.getDistrict(), null));
        } else {
            String districtName = global ? trim(region.getParentDistrict()) : operatingRegion.trim();
            Region parentDistrict = districtName == null ? null : repository.findByNameIgnoreCase(districtName)
                    .filter(parent -> isDistrictLogin(parent))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "A valid district administrator account is required"));
            region.setDistrict(districtLabel(parentDistrict));
            region.setState(parentDistrict.getState());
            if (region.getCountry() == null || region.getCountry().isBlank()) {
                region.setCountry(parentDistrict.getCountry());
            }
            region.setCode(null);
            region.setParentDistrict(parentDistrict.getName());
        }
        normalizeLocation(region);
        if (district || creatingRegion) {
            region.setAccountType("REGION");
        } else {
            region.setAccountType("DISTRICT");
            region.setParentDistrict(null);
            region.setArea(null);
            region.setContact(null);
        }
        return repository.save(region);
    }

    @DeleteMapping("/{id}")
    public void delete(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role,
                       @RequestHeader(value = "X-Operating-Region", defaultValue = "") String operatingRegion,
                       @PathVariable Long id) {
        Region region = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));
        if ("ADMIN".equalsIgnoreCase(role)) {
            if (isDistrictLogin(region)
                    && !repository.findByParentDistrictIgnoreCaseAndAccountTypeIgnoreCase(region.getName(), "REGION").isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Delete or reassign this district's regions before deleting its login");
            }
            repository.delete(region);
        } else if ("REGION_ADMIN".equalsIgnoreCase(role)
                && operatingRegion != null
                && operatingRegion.equalsIgnoreCase(region.getParentDistrict())
                && "REGION".equalsIgnoreCase(region.getAccountType())) {
            repository.delete(region);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owning district can delete this region login");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public Region update(@RequestHeader(value = "X-Admin-Role", defaultValue = "") String role,
                         @RequestHeader(value = "X-Operating-Region", defaultValue = "") String operatingRegion,
                         @PathVariable Long id, @RequestBody Region input) {
        Region region = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Region not found"));
        boolean globalDistrict = "ADMIN".equalsIgnoreCase(role)
                && (region.getAccountType() == null
                    || region.getAccountType().isBlank()
                    || "DISTRICT".equalsIgnoreCase(region.getAccountType()));
        boolean globalRegion = "ADMIN".equalsIgnoreCase(role)
                && "REGION".equalsIgnoreCase(region.getAccountType());
        boolean owningDistrict = "REGION_ADMIN".equalsIgnoreCase(role)
                && operatingRegion != null
                && operatingRegion.equalsIgnoreCase(region.getParentDistrict())
                && "REGION".equalsIgnoreCase(region.getAccountType());
        if (!globalDistrict && !globalRegion && !owningDistrict) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owning administrator can edit this login");
        }
        String name = input.getName() == null ? "" : input.getName().trim();
        String username = input.getUsername() == null ? "" : input.getUsername().trim();
        if (name.isBlank() || username.isBlank()
                || repository.existsByNameIgnoreCaseAndIdNot(name, id)
                || repository.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region name and username must be unique");
        }
        normalizeContact(input);
        String previousDistrictName = region.getName();
        region.setName(name);
        region.setUsername(username);
        region.setCode(globalDistrict
                ? uniqueDistrictCode(districtNameFromLoginName(name), id)
                : null);
        if (globalDistrict) {
            region.setArea(null);
            region.setContact(null);
            region.setDistrict(districtNameFromLoginName(name));
        } else {
            region.setArea(input.getArea());
        }
        region.setContact(input.getContact());
        region.setState(input.getState());
        region.setCountry(input.getCountry());
        if (globalRegion) {
            String districtName = trim(input.getParentDistrict());
            Region parentDistrict = districtName == null ? null : repository.findByNameIgnoreCase(districtName)
                    .filter(this::isDistrictLogin)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Choose a valid district administrator account"));
            region.setParentDistrict(parentDistrict.getName());
            region.setDistrict(districtLabel(parentDistrict));
            region.setState(parentDistrict.getState());
            if (region.getCountry() == null || region.getCountry().isBlank()) {
                region.setCountry(parentDistrict.getCountry());
            }
            region.setCode(null);
        }
        if (input.getPassword() != null && !input.getPassword().isBlank()) {
            region.setPasswordHash(passwordEncoder.encode(input.getPassword()));
        }

        Region saved = repository.save(region);
        if (!previousDistrictName.equalsIgnoreCase(name)) {
            List<Region> children = repository.findByParentDistrictIgnoreCaseAndAccountTypeIgnoreCase(
                    previousDistrictName, "REGION");
            children.forEach(child -> child.setParentDistrict(name));
            repository.saveAll(children);

            List<Shipment> shipments = shipmentRepository
                    .findBySourceRegionIgnoreCaseOrDestinationRegionIgnoreCase(previousDistrictName, previousDistrictName);
            shipments.forEach(shipment -> {
                if (previousDistrictName.equalsIgnoreCase(shipment.getSourceRegion())) {
                    shipment.setSourceRegion(name);
                }
                if (previousDistrictName.equalsIgnoreCase(shipment.getDestinationRegion())) {
                    shipment.setDestinationRegion(name);
                }
            });
            shipmentRepository.saveAll(shipments);
        }
        return saved;
    }

    private void normalizeLocation(Region region) {
        region.setArea(trim(region.getArea()));
        region.setDistrict(trim(region.getDistrict()));
        region.setState(trim(region.getState()));
        region.setCountry(trim(region.getCountry()));
    }
    private void normalizeContact(Region region) {
        String contact = trim(region.getContact());
        if (contact != null && !contact.isBlank() && !contact.matches("[0-9]{10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Contact must contain exactly 10 digits");
        }
        region.setContact(contact == null || contact.isBlank() ? null : contact);
    }
    private boolean isDistrictLogin(Region region) {
        return "DISTRICT".equalsIgnoreCase(region.getAccountType())
                || ((region.getAccountType() == null || region.getAccountType().isBlank())
                    && (region.getParentDistrict() == null || region.getParentDistrict().isBlank()));
    }
    private String districtLabel(Region districtLogin) {
        String district = trim(districtLogin.getDistrict());
        return district == null || district.isBlank()
                ? districtNameFromLoginName(districtLogin.getName())
                : district;
    }
    private String uniqueDistrictCode(String districtName, Long excludeRegionId) {
        String letters = districtName == null
                ? ""
                : districtName.replaceAll("[^A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
        if (letters.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "District name must contain at least three letters to create its district code");
        }

        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        candidates.add(letters.substring(0, 3));
        String distinctLetters = letters.chars()
                .distinct()
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        for (int first = 0; first < distinctLetters.length(); first++) {
            for (int second = 0; second < distinctLetters.length(); second++) {
                for (int third = 0; third < distinctLetters.length(); third++) {
                    candidates.add(new String(new char[] {
                            distinctLetters.charAt(first),
                            distinctLetters.charAt(second),
                            distinctLetters.charAt(third)
                    }));
                }
            }
        }
        for (String candidate : candidates) {
            if (!districtCodeInUse(candidate, excludeRegionId)) return candidate;
        }

        String base = letters.substring(0, 3);
        for (long suffix = 1; ; suffix++) {
            String candidate = base + String.format(java.util.Locale.ROOT, "%03d", suffix);
            if (!districtCodeInUse(candidate, excludeRegionId)) return candidate;
        }
    }

    private String districtNameFromLoginName(String loginName) {
        String districtName = loginName.trim().replaceFirst("(?i)^temi\\s*[-:]\\s*", "").trim();
        if (districtName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "District login name must include a district name");
        }
        return districtName;
    }

    private boolean districtCodeInUse(String code, Long excludeRegionId) {
        return excludeRegionId == null
                ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, excludeRegionId);
    }

    private String trim(String value) { return value == null ? null : value.trim(); }

    private void requireGlobalAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the global admin can manage regions");
        }
    }
}
