/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import org.dspace.app.rest.converter.BitstreamFormatConverter;
import org.dspace.app.rest.converter.MetadataConverter;
import org.dspace.app.rest.exception.UnprocessableEntityException;
import org.dspace.app.rest.model.BitstreamFormatRest;
import org.dspace.app.rest.model.hateoas.BitstreamFormatResource;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.core.Context;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;


/**
 * This is the repository responsible to manage BitstreamFormat Rest object
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@Component(BitstreamFormatRest.CATEGORY + "." + BitstreamFormatRest.NAME)
public class BitstreamFormatRestRepository extends DSpaceRestRepository<BitstreamFormatRest, Integer> {

    @Autowired
    BitstreamFormatService bitstreamFormatService;

    @Autowired
    BitstreamFormatConverter converter;

    @Autowired
    MetadataConverter metadataConverter;

    public BitstreamFormatRestRepository() {
        System.out.println("Repository initialized by Spring");
    }

    @Override
    public BitstreamFormatRest findOne(Context context, Integer id) {
        BitstreamFormat bit = null;
        try {
            bit = bitstreamFormatService.find(context, id);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        if (bit == null) {
            return null;
        }
        return converter.fromModel(bit);
    }

    @Override
    public Page<BitstreamFormatRest> findAll(Context context, Pageable pageable) {
        List<BitstreamFormat> bit = null;
        try {
            bit = bitstreamFormatService.findAll(context);
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        Page<BitstreamFormatRest> page = utils.getPage(bit, pageable).map(converter);
        return page;
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected BitstreamFormatRest createAndReturn(Context context) throws AuthorizeException {
        HttpServletRequest req = getRequestService().getCurrentRequest().getHttpServletRequest();
        ObjectMapper mapper = new ObjectMapper();
        BitstreamFormatRest bitstreamFormatRest = null;
        try {
            ServletInputStream input = req.getInputStream();
            bitstreamFormatRest = mapper.readValue(input, BitstreamFormatRest.class);
        } catch (IOException e1) {
            throw new UnprocessableEntityException("Error parsing request body", e1);
        }

        BitstreamFormat bitstreamFormat = null;
        try {
            bitstreamFormat = bitstreamFormatService.create(context);
            this.setAllValuesOfRest(context, bitstreamFormat, bitstreamFormatRest);
            bitstreamFormatService.update(context, bitstreamFormat);
        } catch (SQLException e) {
            throw new RuntimeException("Unable to create new bitstream format with description: "
                    + bitstreamFormatRest.getShortDescription(), e);
        }

        return converter.convert(bitstreamFormat);
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected BitstreamFormatRest put(Context context, HttpServletRequest request, String apiCategory, String model,
                                      Integer id, JsonNode jsonNode) throws SQLException, AuthorizeException {
        BitstreamFormatRest bitstreamFormatRest = null;
        try {
            bitstreamFormatRest = new ObjectMapper().readValue(jsonNode.toString(), BitstreamFormatRest.class);
        } catch (IOException e) {
            throw new UnprocessableEntityException("Error parsing collection json: " + e.getMessage());
        }
        BitstreamFormat bitstreamFormat = null;
        String notFoundException = "ResourceNotFoundException:" + apiCategory + "." + model
                + " with id: " + id + " not found";
        try {
            bitstreamFormat = bitstreamFormatService.find(context, id);
            if (bitstreamFormat == null) {
                throw new ResourceNotFoundException(notFoundException);
            }
        } catch (SQLException e) {
            throw new ResourceNotFoundException(notFoundException);
        }
        if (bitstreamFormatRest.getId().equals(id)) {
            this.setAllValuesOfRest(context, bitstreamFormat, bitstreamFormatRest);
            bitstreamFormatService.update(context, bitstreamFormat);
            return converter.fromModel(bitstreamFormat);
        } else {
            throw new IllegalArgumentException("The id in the Json and the id in the url do not match: "
                    + id + ", "
                    + bitstreamFormatRest.getId());
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    protected void delete(Context context, Integer id)  throws AuthorizeException {
        BitstreamFormat bitstreamFormat = null;
        String notFoundException = "ResourceNotFoundException:" + BitstreamFormatRest.CATEGORY + "."
                + BitstreamFormatRest.NAME + " with id: " + id + " not found";
        try {
            bitstreamFormat = bitstreamFormatService.find(context, id);
            if (bitstreamFormat == null) {
                throw new ResourceNotFoundException(notFoundException);
            }
        } catch (SQLException e) {
            throw new RuntimeException("RuntimeException: Unable to find BitstreamFormat with id = " + id, e);
        }
        try {
            bitstreamFormatService.delete(context, bitstreamFormat);
        } catch (SQLException e) {
            throw new RuntimeException("RuntimeException: Unable to delete BitstreamFormat with id  = " + id, e);
        }
    }



    private void setAllValuesOfRest
            (Context c, BitstreamFormat bitstreamFormat, BitstreamFormatRest bitstreamFormatRest) {
        try {
            bitstreamFormat.setShortDescription(c, bitstreamFormatRest.getShortDescription());
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        bitstreamFormat.setDescription(bitstreamFormatRest.getDescription());
        bitstreamFormat.setMIMEType(bitstreamFormatRest.getMimetype());
        int supportLevelID = bitstreamFormatService.getSupportLevelID(bitstreamFormatRest.getSupportLevel());
        bitstreamFormat.setSupportLevel(supportLevelID);
        bitstreamFormat.setInternal(bitstreamFormatRest.isInternal());
        bitstreamFormat.setExtensions(bitstreamFormatRest.getExtensions());
    }

    @Override
    public Class<BitstreamFormatRest> getDomainClass() {
        return BitstreamFormatRest.class;
    }

    @Override
    public BitstreamFormatResource wrapResource(BitstreamFormatRest bs, String... rels) {
        return new BitstreamFormatResource(bs, utils, rels);
    }
}