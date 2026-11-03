package no.nav.helse.sykepenger.avslagsdager.leaderelection

internal fun interface LeaderElection {
    fun isLeader(): Boolean
}
