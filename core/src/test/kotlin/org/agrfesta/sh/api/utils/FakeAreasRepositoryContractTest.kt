package org.agrfesta.sh.api.utils

class FakeAreasRepositoryContractTest : AreasRepositoryContract() {
    override val sut = FakeAreasRepository()
}
