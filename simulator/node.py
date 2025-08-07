from simulator.event import Event
from simulator.job import Job
from simulator.scheduler import NextEventScheduler


class Node:
    def __init__(self, name, service_rates):

        """
            The class node which represent a Server
        """

        """
            The node name: Example A - B - P
        """
        self.name = name
        """
            Service rates for a job
        """
        self.service_rates = service_rates
        """
            List of job that can be in queue or in service
        """
        self.jobs: list[Job] = []
        """
            Last update for processor sharing
        """
        self.last_update = 0
        
        self._next_departure: Event = None # ultimo DEPARTURE valido

    def _update_remaining(self, now):
        """
            Update remaining time for a job
        """
        if not self.jobs:
            self.last_update = now
            return
        elapsed = now - self.last_update
        share = elapsed / len(self.jobs)
        """
        Update remaining service time for each job in the queue
        The remaining service time is reduced by the share of the elapsed time
        (ho tolto il mu perché non è necessario per il calcolo del tempo rimanente nel caso PS perchè il service time 
        viene calcolato con idfexp e poi deve essere diminuito in base al tempo
        trascorso diviso per il numero di job in coda
        e non rispetto alla service rate del job)
        """
        for job in self.jobs:
            mu = self.service_rates[job.job_class]
            job.remaining_service = max(0.0,
                job.remaining_service - share)
            """
            DIFFERENCE:
            job.remaining_service = max(0.0,
                job.remaining_service - share * mu)
            """
        self.last_update = now

    def arrival(self, job: Job, scheduler: NextEventScheduler):
        """
        Job Arrival
        :param job: the job that must be scheduled
        :param scheduler: the scheduler to be used
        """
        self._update_remaining(scheduler.current_time)
        self.jobs.append(job)
        self._schedule_departure(scheduler)

    def departure(self, job: Job, scheduler: NextEventScheduler):
        """
        Job departure
        :param job: the job that must be scheduled
        :param scheduler: the scheduler to be used
        """
        
        if job not in self.jobs:
            return   # evento orfano già disattivato, non faccio nulla
        
        self._update_remaining(scheduler.current_time)
        self.jobs = [j for j in self.jobs if j.id != job.id]
        self._schedule_departure(scheduler)

    def _schedule_departure(self, scheduler: NextEventScheduler):
        """
        Schedule departure the job with the shortest remaining time is chosen
        """
        if self._next_departure is not None:
            scheduler.cancel(self._next_departure)
            self._next_departure = None
        
        if not self.jobs: return
        
        #anche qui va tolto il mu perchè non è necessario per il calcolo del tempo rimanente nel caso PS
        #guarda commento multilinea sotto per capire  meglio e anche quello in update_remaining
        min_job = min(self.jobs, key=lambda j: j.remaining_service)
        """
        min_job = min(self.jobs,
              key=lambda j: j.remaining_service / self.service_rates[j.job_class])
        """
        
        """
        Ho cambiato il calcolo del ttf (time to finish) per il caso PS perchè veniva diviso per il service rate
        quando in realtà in quel calcolo la media del tempo di servizio del job non deve essere considerata
        ma solo il tempo rimanente del job stesso moltiplicato per quanti job ci sono
        esempio numerico:j1 con remaining_service=10, j2 con remaining_service=20,
        ttfjob1 = (10 * 3) = 30 e non (10*3 / service_rate) = 30 / service_rate
        In questo caso il ttf è 20 e non 10 come veniva calcolato prima
        """
        ttf = min_job.remaining_service * len(self.jobs)
        """
        DIFFERNCE:
        ttf = (min_job.remaining_service * len(self.jobs)
               / self.service_rates[min_job.job_class])
        """

        e = Event(time=scheduler.current_time + ttf,
                  event_type="DEPARTURE",
                  server=self.name,
                  job_id=min_job.id,
                  job_class=min_job.job_class)
        self._next_departure = e
        scheduler.schedule(e)


    def __repr__(self):
        return f"Node(name={self.name}, service_rates={self.service_rates}, last_update={self.last_update})"

    def __str__(self):
        return self.__repr__()