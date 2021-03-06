/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.syncope.core.persistence.beans.membership;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.syncope.core.persistence.beans.AbstractDerSchema;
import org.syncope.core.persistence.beans.AbstractSchema;

@Entity
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
public class MSchema extends AbstractSchema {

    @ManyToMany(mappedBy = "schemas")
    private List<MDerSchema> derivedSchemas;

    public MSchema() {
        derivedSchemas = new ArrayList<MDerSchema>();
    }

    @Override
    public <T extends AbstractDerSchema> boolean addDerivedSchema(
            final T derivedSchema) {

        return derivedSchemas.add((MDerSchema) derivedSchema);
    }

    @Override
    public <T extends AbstractDerSchema> boolean removeDerivedSchema(
            final T derivedSchema) {

        return derivedSchemas.remove((MDerSchema) derivedSchema);
    }

    @Override
    public List<? extends AbstractDerSchema> getDerivedSchemas() {
        return derivedSchemas;
    }

    @Override
    public void setDerivedSchemas(
            final List<? extends AbstractDerSchema> derivedSchemas) {

        this.derivedSchemas.clear();
        if (derivedSchemas != null && !derivedSchemas.isEmpty()) {
            this.derivedSchemas.addAll(
                    (List<MDerSchema>) derivedSchemas);
        }
    }
}
